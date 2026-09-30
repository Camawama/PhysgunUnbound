# Physgun Unbound

An addon for [VMod](https://modrinth.com/mod/vmod) (SpaceEye's Valkyrien Skies addon), for Forge 1.20.1. VMod's physgun picks up and moves **entities** too, not only ships. You hold them, pull them in, push them out, turn them, freeze them in mid-air and throw them, with the physgun's own buttons, and they move the way a held ship moves.

- **Mod id:** `physgununbound`
- **Package:** `net.camacraft.physgununbound`
- **Author:** Camawama
- **Licence:** GPL-3.0 (see `LICENSE.txt`, the same licence as Camafy). To change it, edit `mod_license` in `gradle.properties`, replace `LICENSE.txt`, and update this line.
- **Requires:** Minecraft 1.20.1, Forge 47.4 or later, and [Camafy](../../CAMAFY) 0.1.13 or later (below 0.2). It only does something when VMod is installed. It is built and tested against VMod 1.9.1, and the parts of VMod it uses are the same in 1.7.1, 1.8.0 and 1.9.0.
- **Sides:** both, but the server does the work. A client without the mod can still join. It just sees no beam to held entities, and when held it is moved by vanilla motion packets. A client with the mod can join servers without it.

## Controls

These are VMod's physgun controls. Nothing new to learn: the same buttons that work on ships now work on entities.

| Input (physgun in the main hand) | On an entity |
|---|---|
| Hold **left mouse** on an entity | Pick it up at the point you aimed at. It is held at that distance in front of your eyes and follows your view. |
| Move / look around | The grabbed point follows the crosshair. Turning also turns the entity with you. |
| **Mouse wheel** while holding | Push it out or pull it in, one block per notch (from 1 to 128 blocks, see config). |
| Hold **E** and move the mouse while holding | Rotate it (the camera stays still, as with ships). Entities show the turn as their heading, and mobs also as their head's pitch. |
| **Right click** (or middle click) while holding | Freeze it where it is and let go. A frozen entity hangs in the air and doesn't move by itself (no AI, no gravity) until unfrozen. |
| **R** with the left button up, aiming at a frozen entity | Unfreeze it: it falls, walks and thinks again. Grabbing a frozen entity also unfreezes it. |
| Let go of **left mouse** | Let go. The entity keeps the speed it had, so swinging the physgun and letting go throws it, as in Garry's Mod. |

Seeking works as VMod's does. Hold the left button and sweep the crosshair, and the physgun takes the first thing it touches. If an entity is in front of a ship block, the entity is picked up. If nothing is in front of the ship, VMod picks up the ship as usual.

## What it picks up

- **Yes:** mobs and animals, items, experience orbs, boats, minecarts (a cart on a rail is lifted off it), falling blocks (held sand doesn't land or run out of air time), primed TNT (it still explodes), armor stands, end crystals, and other players.
- **Stacks:** aiming at something that rides something picks up the whole stack from the bottom. A zombie on a chicken lifts both, and a player on a horse lifts the horse with them on it.
- **Entities on ships:** a mob standing on a Valkyrien Skies deck is picked up, and Valkyrien Skies stops carrying it with the ship.
- **Never:**
  - the holder, or anything carrying the holder;
  - spectators;
  - hanging entities (item frames, paintings, lead knots), which break if moved;
  - projectiles (arrows, tridents, fireballs), which move themselves;
  - sleeping mobs and players;
  - entities that are part of a ship (they live in its shipyard: seats, frames);
  - anything another mod moves: Camafy's `MOTION` claim (MKW's karts and drivers, a player on a Grapple Unbound rope);
  - anything the gravity mod anchors to a surface (standing on Gravity Unbound's plating, Surface Cling);
  - anything a minigame protects (Camafy's `MatchPolicies.canHarm`, which Game Start provides);
  - types in `#physgununbound:ungrabbable`, or in the `ungrabbable` config list. The tag holds the ender dragon, markers, displays, interaction boxes, area clouds, evoker fangs, lightning, and Create's contraptions, trains, seats and glue.
- **Players:** only if `grabPlayers` is on (it is by default) and every minigame host allows it. A held player can't be frozen; right click just lets them go.

## How a held entity moves

VMod's `PhysgunController` pulls a ship's grabbed point toward the point in front of the player with a spring and a damper, on Valkyrien Skies' physics thread, 60 steps a second:

```
force = (spring × (target − grabbed point) − damping × velocity) × mass
```

The mass cancels out, so every ship accelerates the same way. With VMod's defaults (`PCONST` 160, `DCONST` 20), it settles in about a third of a second and barely overshoots.

A held entity runs the same law:

- **Same steps:** three 1/60 s steps per tick.
- **Same constants:** the ones VMod's server config gives the physgun, so an entity moves exactly as a ship does on that server.
- **Collisions:** the entity is moved through its own collision code, so walls, floors and ship hulls stop it. The part of its speed going into the wall is lost.
- **Top speed:** `maxSpeed`, 70 blocks a second by default.
- **Its own movement is paused:** the entity doesn't walk, fall or drift while held. The rest of its tick still runs: a creeper still fuses, a zombie still swings, and an item still ages.
- **Turning:** the entity keeps its facing relative to you as you turn, like a held ship. Looking up and down doesn't tilt it, because entities can't lean. The E-drag turns it freely.

**Players** are moved by their own client, where their movement is simulated:

- **With the mod:** the server sends the target each tick, and the client runs the same spring from where it really is, with no round trip in the loop.
- **Without the mod:** vanilla motion packets steer them from the position they last reported.
- **Flying:** a player held in the air isn't kicked for flying on servers with `allow-flight=false`.
- **Fall damage:** none builds up while held. After being let go they fall (and crash) normally.

## Multiplayer and cleanup

- **Server-authoritative:** the server decides everything. It reads the physgun's inputs from VMod's own messages and checks them (a NaN or huge wheel value is clamped, a malformed rotation is ignored). Messages from a player without the physgun in hand start nothing.
- **One hold per holder:** a second player grabbing a held entity takes it over, as VMod does with ships.
- **When a hold ends:** when the holder lets go, logs out, dies, changes dimension, switches away from the physgun or turns spectator. It also ends when the entity dies, is removed, is unloaded, changes dimension, starts riding something, or is taken by a player's steering (someone climbs into the held boat and takes the reins).
- **Server stop:** every hold is ended before the server saves, so held entities are written with their own gravity back. If the server crashes mid-hold, the entity gets its gravity back the next time it loads (an entity tag, `physgununbound.held`, marks it).
- **Frozen entities stay frozen through saves.** They carry the entity tag `physgununbound.frozen` (and `physgununbound.frozen.had_no_gravity` / `.had_no_ai` for what they had before), which `/tag` shows. Only entities with that tag are ever unfrozen by the physgun: a map maker's NoAI villager stays as it is.

## Compatibility

- **VMod:**
  - **The hook:** one mixin reads the physgun's buttons at the end of `PlayerPhysgunState.fromPkt`, where VMod's own handler reads them. The mod calls a few public methods on VMod's `ServerPhysgunState` and `PlayerPhysgunState`, and removes VMod's own seeking beam while an entity is held. Everything else is VMod's, untouched: ships, the toolgun, the physgun's client side.
  - **If VMod changes:** the hook is in its own mixin config that isn't required, and names its target by its full descriptor. If a class, method or field it uses is missing after a VMod update, the server logs one line at start ("VMod's physgun doesn't work the way Physgun Unbound expects ...") and the addon does nothing. VMod keeps working.
- **Valkyrien Skies:**
  - **Through Camafy:** the entity search ends at the first world or ship block (Camafy's `Ships.clip`), entities in a shipyard are left to their ship, and a grabbed entity is released from VS's deck carrying (`ShipEntities.letGo`) on the side that simulates it.
  - **Ship hulls:** held entities collide with ship hulls through VS's own collision hook.
  - **No direct VS imports:** the mod never imports Valkyrien Skies. Only the opt-in ship test does, to build a raft.
- **Camafy rules:**
  - **Physics claims:** a held entity is claimed for every physics aspect (`PhysicsOwnership`), so Full Stop, WallJump, Grapple Unbound and Gravity Unbound's movement leave it alone while it is held. The client claims its own player while held. A frozen entity is claimed for `MOTION`.
  - **After the throw:** once let go, Full Stop starts afresh with the throw (being swung into a wall while held does no crash damage, but a thrown mob hitting a wall does).
  - **Match rules:** grabbing asks `MatchPolicies.canHarm` for the entity and every rider, and so does unfreezing.
  - **Resized entities:** held entities move the real distance whatever Pehkui did to their size (`EntityScale`).
- **Vanilla Backport:** no overlap (it doesn't touch entity movement or VMod).
- **Mixins:**
  - `Entity.move` (HEAD, cancellable): skips a held or frozen entity's own `MoverType.SELF` move on the server.
  - `ServerGamePacketListenerImpl.tick` (HEAD): resets the flying-kick counters of held players.
  - `PlayerPhysgunState.fromPkt` (TAIL): VMod's messages.

## Config

`serverconfig/physgununbound-server.toml`, section `physgun`:

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Whether the physgun picks up entities at all. |
| `grabPlayers` | `true` | Whether players (and anything a player rides) can be picked up. Minigame hosts can still forbid it per player. |
| `range` | `100` | How far away an entity can be picked up, in blocks (VMod's own reach). |
| `minDistance` | `1` | The closest an entity can be pulled in (VMod lets ships come to 0; an entity that close would sit inside you). |
| `maxDistance` | `128` | The farthest it can be pushed out. It stops at the edge of loaded chunks. |
| `maxSpeed` | `70` | The fastest a held entity moves, and so the fastest throw, in blocks per second (3.5 blocks a tick). |
| `fallbackSpring`, `fallbackDamping` | `160`, `20` | The hold's constants when VMod's own (`PCONST`, `DCONST` in VMod's server config) can't be read. |
| `ungrabbable` | `["#physgununbound:ungrabbable"]` | Entity ids, `#tags`, wildcards (`create:*`) or `/regex/` never picked up. |

## Building and testing

```
./gradlew build                        # the release jar: build/libs/physgununbound-<version>.jar
./gradlew runGameTestServer            # 12 gametests of the hold itself, no VMod needed
./gradlew runGameTestServer -Pvmod     # the same plus 2 with the real VMod, VS, Kotlin for Forge and Architectury
```

- **Default suite (`src/main/java/.../gametest`):**
  - a held mob follows the target and swings round with the holder;
  - letting go throws it with the hold's speed and clears every claim;
  - the wheel and the rotate drag;
  - walls stop it;
  - items, a cart on a rail, a boat, falling sand and an armor stand are held (and survive it);
  - a held player is steered and claimed;
  - the refusals (self, spectator, item frame, interaction box, another mod's claim, a minigame's rule);
  - removal and a dead holder end the hold;
  - freezing, then unfreezing with R;
  - a second holder takes over;
  - the crash-recovery mark.
- **`-Pvmod` (`src/vmodTest`):**
  - hands messages built like VMod's client builds them to VMod's own registered handler: seeking grabs a pig, VMod stands down while it is held, then the wheel, the rotate drag, freezing, unfreezing and a throw;
  - builds a real VS raft: a pig standing on its deck is picked up instead of the raft, follows the physgun off the deck, can't be pressed into the deck (it collides with the hull), and aiming at the raft with nothing in front still gives VMod the raft.
  - A jar built with `-Pvmod` would carry those tests, so the `jar` task refuses to run with the flag.
- **Refmap check:** the build checks the jar contains its mixin refmap.

The gametest world is flat and fresh each run (`run-gametest`). In-game checks are in `TESTING_CHECKLIST.md`.

## Layout

- `hold/`: the holds and their rules.
  - `Holds`: grab, tick, input, end.
  - `HoldMotion`: VMod's spring and damper, the orientation maths.
  - `Grabbing`: the ray and the refusals.
  - `Freezing`, `HeldEntities`: the claims and the move stop.
  - `HoldEvents`: the Forge events.
  - `PhysgunInput`: one physgun message.
- `vmod/`: the bridge to VMod. `Vmod` is the only door. `VmodBridgeImpl` and `VmodPackets` name VMod's classes and only load with VMod.
- `mixin/`, `mixin/vmod/`: the three hooks, in two configs.
- `network/`: the beam message and the held-player message (Camafy channel `physgununbound:main`, optional on both sides).
- `client/`: the beam (drawn like VMod's `PhysgunRayRenderer`) and the held player's side of a hold.

## Known limits

- Entities can't lean, so the rotate key turns only their heading (and a mob's head up and down). A held ship turns every way.
- A held vehicle a player drives (a boat, a horse) is moved by that player's client, which applies its own boat physics on top, so it hangs a little lower than a mob would.
- Projectiles can't be picked up.
- Without this mod on a held player's client, the hold works from positions that arrive a ping late, so it can wobble on a laggy connection.
