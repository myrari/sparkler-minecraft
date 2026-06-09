package net.myrari.sparkler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

class PairResponse {
	String secret;
	String error;
}

class HitRequest {
	double intensity;
	double duration;

	HitRequest(double i, double d) {
		this.intensity = i;
		this.duration = d;
	}
}

public class SparklerClient implements ClientModInitializer {
	public static final String MOD_ID = "sparkler";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private String secret;

	private static void sendHit(HttpClient httpClient, String host, String secret, UUID uuid, float dmg, float to) {
		URI uri = URI.create(host + "/sparkle");

		Gson gson = new Gson();

		HitRequest hitReq = new HitRequest(dmg * 5, 1);
		String bodyString = gson.toJson(hitReq);

		LOGGER.info("body: " + bodyString);

		HttpRequest req = HttpRequest.newBuilder()
				.uri(uri)
				.header("Content-Type", "application/json")
				.header("secret", secret)
				.POST(BodyPublishers.ofString(bodyString))
				.build();

		var futureRes = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());

		futureRes.thenAccept((res) -> {
			int status = res.statusCode();
			if (status == 200) {
				LOGGER.info("Successfully sent hit for " + dmg);
			} else {
				LOGGER.warn("Tried to send hit, but got error code: " + status);
			}
		});
	}

	private static void pair(HttpClient httpClient, String host, String pairingCode, Function<String, Void> callback) {
		URI uri = URI.create(host + "/auth");

		LOGGER.info("pairing URI: " + uri.toString());

		Gson gson = new Gson();

		HttpRequest req = HttpRequest.newBuilder()
				.uri(uri)
				.header("Content-Type", "application/json")
				.header("pairing-code", pairingCode)
				.POST(BodyPublishers.noBody())
				.build();

		var futureRes = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());

		futureRes.thenAccept((res) -> {
			int status = res.statusCode();
			PairResponse resBody = gson.fromJson(res.body(), PairResponse.class);
			if (status == 200) {
				LOGGER.info("Succesfully paired!");
				callback.apply(resBody.secret);
			} else {
				LOGGER.error("Pairing error " + status + ": " + resBody.error);
				callback.apply("");
			}
		});
	}

	@Override
	public void onInitializeClient() {
		final String HOST = "https://sparkler.myrari.net";

		UUID uuid = Minecraft.getInstance().getGameProfile().id();

		LOGGER.debug("Found player uuid: " + uuid);

		HttpClient httpClient = HttpClient.newHttpClient();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
					ClientCommandManager.literal("sparkler_pair")
							.executes(ctx -> {
								LOGGER.warn("Called /sparkler_pair with no pairing code");
								ctx.getSource().sendError(Component.literal("Please provide a pairing code!"));
								return 1;
							})
							.then(ClientCommandManager.argument("pairing_code", StringArgumentType.string())
									.executes(ctx -> {
										String pairingCode = StringArgumentType.getString(ctx, "pairing_code");
										LOGGER.info("Called /sparkler_pair, pairing code: " + pairingCode);
										pair(httpClient, HOST, pairingCode, s -> {
											if (s == null || s == "") {
												// there was some error!
												ctx.getSource().sendError(Component.literal(
														"There was an error pairing to Sparkler! Check the console for more details."));
											} else {
												// we got the secret!
												secret = s;
												ctx.getSource().sendFeedback(
														Component.literal("Sparkler paired successfully!"));
											}
											return null;
										});
										return 1;
									})));
		});

		PlayerHurtCallback.EVENT.register((player, dmg, to) -> {
			if (uuid.compareTo(player.getUUID()) == 0) {
				LOGGER.debug("player hurt for " + dmg);
				if (secret != null && secret != "") {
					sendHit(httpClient, HOST, secret, uuid, dmg, to);
				}
			}
		});
	}
}