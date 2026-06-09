# ✨Sparkler✨

This is a clientside Fabric mod for Minecraft that connects the game to the
[Sparkler](https://github.com/myrari/sparkler) API to trigger real-world events
whenever you take damage in-game.

The mod is fully client-side (currently set up to be built for version 1.21.10)
that uses a small mixin to detect when the client player loses health by
reading the hotbar GUI. It then uses the built-in Java HTTP Client to send an
HTTP post request to the control server, containing information on which player
was hit, for how much damage, and how much health they have remaining.

## Usage

Once you have created a new session on the [Sparkler
website](https://sparkler.myrari.net), copy the **pairing code** and call the
`/sparkler_pair <pairing code>` command with it in-game. If all goes well your
game should then be paired to your Sparkler session, and send commands whenever
you take damage!

It is currently set up to scale the intensity of Sparkler commands with the
amount of damage taken, but the duration is always 1 second.

For more information on how Sparkler works, check out the [main control
server](https://github.com/myrari/sparkler).
