# Physgun Unbound

An addon for [VMod](https://modrinth.com/mod/vmod) (SpaceEye's Valkyrien Skies addon), for Forge 1.20.1. VMod's physgun picks up and moves **entities** too, not only ships. You hold them, pull them in, push them out, turn them, freeze them in mid-air and throw them, with the physgun's own buttons, and they move the way a held ship moves.

- **Mod id:** `physgununbound`
- **Package:** `net.camacraft.physgununbound`
- **Author:** Camawama
- **Licence:** GPL-3.0 (see `LICENSE.txt`, the same licence as Camafy). To change it, edit `mod_license` in `gradle.properties`, replace `LICENSE.txt`, and update this line.
- **Requires:** Minecraft 1.20.1, Forge 47.4 or later, and [Camafy](../../CAMAFY) 0.1.13 or later (below 0.2). It only does something when VMod is installed. It is built and tested against VMod 1.9.1, and the parts of VMod it uses are the same in 1.7.1, 1.8.0 and 1.9.0.
- **Sides:** both, but the server does the work. A client without the mod can still join. It just sees no beam to held entities, draws them upright however they are turned, and when held it is moved by vanilla motion packets. A client with the mod can join servers without it. Server and client with the mod need the same version (network protocol "2").

## Controls

These are VMod's physgun controls. Nothing new to learn: the same buttons that work on ships now work on entities.

| Input (physgun in the main hand) | On an entity |
|---|---|
| Hold **left mouse** on an entity | Pick it up at the point you aimed at. It is held at that distance in front of your eyes and follows your view. |
| Move / look around | The grabbed point follows the crosshair. Turning also turns the entity with you. |
| **Mouse wheel** while holding | Push it out or pull it in, one block per notch (from 1 to 128 blocks, see config). |
| Hold **E** and move the mouse while holding | Rotate it every way, as a held ship rotates (the camera stays still). Its near side follows the mouse: left, right, up (the top tips away) and down, for ships too (VMod's own sense moved the far side instead; `rotation.invertMouseX` / `invertMouseY` in the client config bring it back per axis). Looking up and down tips it with your view as well, like a ship (`followViewPitch`). Let go and it rolls back upright over half a second (`rotation.returnTicks`). |
| **Right click** (or middle click) while holding | Freeze it where it is and let go. A frozen entity hangs in the air, turned the way you held it, and doesn't move by itself (no AI, no gravity) until unfrozen. |
| **R** with the left button up, aiming at a frozen entity | Unfreeze it: it rolls upright, falls, walks and thinks again. Grabbing a frozen entity also unfreezes it, and carries on from the way it was turned. |
| Let go of **left mouse** | Let go. The entity keeps the speed it had, so swinging the physgun and letting go throws it, as in Garry's Mod. |

Seeking works as VMod's does. Hold the left button and sweep the crosshair, and the physgun takes the first thing it touches. If an entity is in front of a ship block, the entity is picked up. If nothing is in front of the ship, VMod picks up the ship as usual.

## What it picks up

- **Yes: anything with a body**, from any mod, unless it is on the list below. Mobs and animals, items, experience orbs, boats, minecarts (a cart on a rail is lifted off it), falling blocks (held sand doesn't land or run out of air time), primed TNT (it still explodes), armor stands, end crystals, other players, modded vehicles (Automobility's cars), and **projectiles in flight**: arrows, tridents, snowballs, eggs, ender pearls, potions, fireballs, wither skulls, shulker bullets, fireworks, and modded ones built on those. A held projectile stops flying (no gravity, no acceleration, it hits nothing, not even you) and sits where you put it; let go, it flies on with the throw, an arrow aiming itself along it.
- **Stacks:** aiming at something that rides something picks up the whole stack from the bottom. A zombie on a chicken lifts both, and a player on a horse lifts the horse with them on it.
- **Entities on ships:** a mob standing on a Valkyrien Skies deck is picked up, and Valkyrien Skies stops carrying it with the ship.
- **Never:**
  - the holder, or anything carrying the holder;
  - spectators;
  - hanging entities (item frames, paintings, lead knots), which break if moved;
  - an arrow stuck in a block;
  - sleeping mobs and players;
  - entities that are part of a ship (they live in its shipyard: seats, frames);
  - anything another mod scripts the whole physics of, every aspect of Camafy's `PhysicsOwnership` (a kart in an MKW race, MKW's hazards). A claim on its motion alone doesn't stop the physgun: the hold claims everything, and the mods that arbitrate `MOTION` (WallJump, Grapple Unbound) stand down for it. MKW's free-roam karts don't stand down yet: while held, the kart can't move itself (its own `move` is skipped), but MKW keeps driving it underneath, so a thrown kart drops rather than flies;
  - anything the gravity mod anchors to a surface (standing on Gravity Unbound's plating, Surface Cling);
  - anything a minigame protects (Camafy's `MatchPolicies.canHarm`, which Game Start provides);
  - types in `#physgununbound:ungrabbable`, or in the `ungrabbable` config list. The tag holds the ender dragon; markers, displays and interaction boxes (data only); item frames, paintings and lead knots; fishing bobbers and eyes of ender (they fly themselves); and Create's contraptions, trains, seats and glue.
- **How the tag applies to modded entities:** every entity has a registry id (`automobility:automobile`, `supplementaries:bomb`). The tag is the data file `data/physgununbound/tags/entity_types/ungrabbable.json`, a list of those ids; a datapack (or another mod's data) can add ids to it with a file of the same name (`"replace": false`), and an entry written as `{ "id": "somemod:thing", "required": false }` is skipped when that mod isn't installed. Anything not listed is picked up, so a modded entity needs no entry to work. The `ungrabbable` config list does the same without a datapack, and takes wildcards (`create:*`) and `/regex/`.
- **Players:** only by staff with the permission `physgununbound.grab.players`, or by anyone within `playerRange` if `playersCanBePickedUp` is on (it is off by default), and only if every minigame host allows it. A hold ends when a player it may not carry mounts what is held, or joins a match that protects them. A held player can't be frozen; right click just lets them go.

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
- **A shrunk world** (VS Genesis's Great Unknown, where players, mobs and ships are 1/16 size; or any size mod): the physgun's lengths are the holder's own (`scaleWithHolder`, on). A player at 1/16 pulls things in to 1/16 of `minDistance`, pushes them out to 1/16 of `maxDistance`, moves them 1/16 of a block per wheel notch (a held ship too) and holds and throws them at up to 1/16 of `maxSpeed`. The spring and the damper need no scaling (they have no length in them), so the same hand movement does at 1/16 what it does at full size, at 1/16 of the size. At full-size lengths a 1/16 player's grab was pushed out to sixteen of their own blocks, every small turn of the view swept the held thing 16 times too far and too fast, it was thrown with that speed, and the wheel moved a held ship 16 of their blocks a notch. The beams follow the holder's size too (whatever the config): VMod's beam to a ship and this mod's beam to an entity are 1/16 as thick and leave the gun 1/16 as far from the camera, so they look the same against a shrunk world as against a full-size one; another player's beam by that player's size. The holder's size is read from its box (Camafy's `EntityScale`), not from Pehkui's motion scale.
- **Its own movement is paused:** the entity doesn't walk, fall or drift while held. The rest of its tick still runs: a creeper still fuses, a zombie still swings, and an item still ages.
- **Turning:** the entity keeps its orientation relative to your view as you turn, like a held ship: turning turns it, and looking up and down tips it (`followViewPitch`, on by default; off keeps it level). The E-drag turns it every way. What its own renderer can show goes into the entity itself (its heading; for an arrow the heading and the pitch), so a client without the mod and the entity's own code see it face the right way; the rest of the turn, the *model rotation*, is drawn by clients with the mod on top, about the entity's centre (`hold/ModelPose`, `client/ClientModelRotations`). Its hitbox never turns, and neither does a model that faces the camera (a thrown egg or snowball, a dropped item, an orb, a fireball, a bobber, a firework: their renderer class, or the entity tag `#physgununbound:billboards` for other mods' billboards), which would stop facing you if it were; such an entity still carries its orientation and its shadow follows it. Let go, or unfrozen, it eases back upright over `rotation.returnTicks` (client, 10). A frozen entity keeps its turn (in its persistent data, so through saves; every client that starts seeing it is told), and a hold taken out of a freeze, or over from another player, carries on from it.

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
  - **The hook:** one mixin reads the physgun's buttons at the end of `PlayerPhysgunState.fromPkt`, where VMod's own handler reads them. The mod calls a few public methods on VMod's `ServerPhysgunState` and `PlayerPhysgunState`, and removes VMod's own seeking beam while an entity is held. Everything else is VMod's, untouched: ships, the toolgun.
  - **The wheel on a held ship:** VMod adds each wheel notch to its hold distance as a full block. The same hook (`fromPkt`, TAIL, before VMod's handler applies it) scales the notch to the holder's size, so a shrunk player's wheel moves a shrunk ship in their own blocks (`scaleWithHolder`).
  - **The rotate drag's sense:** one client mixin negates both mouse angles where VMod's client builds the drag's rotation (`ClientPhysgunState.handleMouseMovement`), so the near side follows the mouse, left and right, up and down, for ships and entities alike (`rotation.invertMouseX` / `invertMouseY` restore VMod's sense per axis). The method is named as Kotlin compiles it (`handleMouseMovement$VMod`); if a VMod update renames it, the drag keeps VMod's sense and nothing else changes.
  - **VMod's physgun messages on the server thread** (2026-10-30, after a server froze for good): VMod handles its physgun message on the network thread inside `synchronized (state.getLock())`, and for R with the left button up it ray-casts there, through the shipyard of every ship near the ray. A shipyard chunk that isn't loaded makes the network thread wait for the server thread, which takes the same lock at the start of every tick for every player holding a ship (VMod's own physgun tick; this mod's let-go of held ships that are gone did too): neither thread moves again. A second hook at the head of VMod's handler (`ServerPhysgunState$special$$inlined$regC2S$1.serverHandler`, by name, optional) copies a message that arrives on any other thread and hands it to the server, in order and between ticks as vanilla hands over its own game packets, where VMod's handler runs it (`vmod/VmodMessages`); the ray-cast then loads what it reads on the server thread. Switch `[vmod] messagesOnServerThread` (on). This mod itself never waits for VMod's lock on the server thread, switch or not. Pack harness `physgun_deadlock` reproduces the freeze.
  - **If VMod changes:** the hook is in its own mixin config that isn't required, and names its target by its full descriptor. If a class, method or field it uses is missing after a VMod update, the server logs one line at start ("VMod's physgun doesn't work the way Physgun Unbound expects ...") and the addon does nothing. VMod keeps working.
- **Valkyrien Skies:**
  - **Through Camafy:** the entity search ends at the first world or ship block (Camafy's `Ships.clip`), entities in a shipyard are left to their ship, and a grabbed entity is released from VS's deck carrying (`ShipEntities.letGo`) on the side that simulates it.
  - **Ship hulls:** held entities collide with ship hulls through VS's own collision hook.
  - **No direct VS imports:** the mod never imports Valkyrien Skies. Only the opt-in ship test does, to build a raft.
- **Camafy rules:**
  - **Physics claims:** a held entity is claimed for every physics aspect (`PhysicsOwnership`), so Full Stop, WallJump, Grapple Unbound and Gravity Unbound's movement leave it alone while it is held. The client claims its own player while held. A frozen entity is claimed for `MOTION`. The claim never lapses between a hold and the freeze it ends in, or between one player's hold and another's taking it over (both happen within one call on the server thread).
  - **Gravity frames (Gravity Unbound):** the hold works in world axes whatever frame the gravity mod gives the entity. A move it makes goes through Camafy's `Gravity.worldToMovement` first, because the gravity mod reads a `MoverType.SELF` movement in the entity's movement convention and turns it into the world (that convention stays in force for a claimed entity: it is a coordinate system, not a force), and every velocity it writes goes through `Gravity.setWorldVelocity`, so a cow held in a sideways plating field follows the beam exactly and a throw out of the field leaves with the throw's speed, not the throw turned by the field. What the gravity mod must do for a claimed entity: no forces, no anchoring, no carry with a ship, no velocity edits, and keep its movement convention and `movementToWorld`/`worldToMovement` consistent with each other.
  - **After the throw:** once let go, Full Stop starts afresh with the throw (being swung into a wall while held does no crash damage, but a thrown mob hitting a wall does).
  - **Match rules:** grabbing asks `MatchPolicies.canHarm` for the entity and every rider, and so does unfreezing.
  - **Resized entities:** held entities move the real distance whatever Pehkui did to their size (`EntityScale`).
- **Vanilla Backport:** no overlap (it doesn't touch entity movement or VMod).
- **Mixins:**
  - `Entity.move` (HEAD, cancellable): skips a held or frozen entity's own `MoverType.SELF` move on the server.
  - `AbstractArrow.tick`, `ThrownTrident.tick`, `ThrowableProjectile.tick`, `ThrownEnderpearl.tick`, `AbstractHurtingProjectile.tick`, `ShulkerBullet.tick`, `LlamaSpit.tick`, `FireworkRocketEntity.tick` (HEAD, cancellable): a held or frozen projectile runs only `Entity.baseTick` (projectiles move themselves in their ticks, not through `move`; a trident's Loyalty return and a pearl's owner check live in their own overrides, hence those two). On the server while it is held or frozen; on a client while that client knows it is (it sees the beam, or was told the frozen pose), since the client's copy would otherwise move and turn itself along a zero velocity against what the server sends.
  - `AbstractArrow.inGround` (accessor): stuck arrows aren't picked up.
  - `ServerGamePacketListenerImpl.tick` (HEAD): resets the flying-kick counters of held players.
  - `EntityRenderDispatcher.render` (client, around the renderer's `render` call): draws a held or frozen entity's model turned, and (a `@Redirect` of the dispatcher's own `renderShadow` call) its shadow as an ellipse along the way it lies (Camafy's `BlobShadow`, on a ship's deck when the entity is held over one: `ShipShadows.under`, Camafy 0.1.18); the hitbox and name tag stay put, GUI portraits (Camafy's `GuiEntityRender`) are left alone, and so are renderers that face the camera (`ClientModelRotations.drawsTurned`). Gravity Unbound wraps the same shadow call and hands ordinary-gravity entities on to it.
  - `PlayerPhysgunState.fromPkt` (TAIL): VMod's messages.
  - VMod's physgun message handler `serverHandler` (HEAD, cancellable, optional): a message arriving off the server thread is handed to the server thread.
  - `ClientPhysgunState.handleMouseMovement$VMod` (client, two `@ModifyArg`s, optional): the rotate drag's sense on both mouse axes.
  - `PhysgunRayRenderer.renderData` (client, four `@ModifyConstant`s, optional): VMod's beam at its holder's size (its 0.1-block thickness and its first-person start, 0.2 below, 0.6 right of and 0.45 in front of the camera).

## Config

`serverconfig/physgununbound-server.toml`, section `physgun`:

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Whether the physgun picks up entities at all. |
| `playersCanBePickedUp` | `false` | Whether players (and anything a player rides) can be picked up by anyone. Off, only staff with the permission `physgununbound.grab.players` do (operators have it; a permissions mod such as LuckPerms can give it to others; `/camafy permissions` lists it). Minigame hosts still forbid it per player, for staff too. Was `grabPlayers`, on by default, until 1.0.1. |
| `range` | `100` | How far away an entity can be picked up, in blocks (VMod's own reach). |
| `playerRange` | `16` | How far away a player (or what a player rides) can be picked up with `playersCanBePickedUp` on. Staff with the permission reach as far as `range`. |
| `minDistance` | `1` | The closest an entity can be pulled in (VMod lets ships come to 0; an entity that close would sit inside you). |
| `maxDistance` | `128` | The farthest it can be pushed out. It stops at the edge of loaded chunks. |
| `maxSpeed` | `70` | The fastest a held entity moves, and so the fastest throw, in blocks per second (3.5 blocks a tick). |
| `fallbackSpring`, `fallbackDamping` | `160`, `20` | The hold's constants when VMod's own (`PCONST`, `DCONST` in VMod's server config) can't be read. |
| `followViewPitch` | `true` | A held entity tips with the holder's view when they look up and down, as a ship does. Off, it only turns with the view's heading. |
| `scaleWithHolder` | `true` | The physgun's lengths are the holder's own size: a 1/16 player (VS Genesis's Great Unknown) gets 1/16 of `minDistance`, `maxDistance` and `maxSpeed`, and 1/16 of a block per wheel notch, for entities and for ships held through VMod. Off, they are always full-size blocks. |
| `ungrabbable` | `["#physgununbound:ungrabbable"]` | Entity ids, `#tags`, wildcards (`create:*`) or `/regex/` never picked up. |

Section `vmod`:

| Key | Default | What it does |
|---|---|---|
| `messagesOnServerThread` | `true` | VMod's physgun messages are handled on the server thread instead of the network thread, so VMod's R ray-cast can't freeze the server waiting for a chunk under its lock. `-Dphysgununbound.vmodMessagesOnServerThread=true|false` overrides it. |

`config/physgununbound-client.toml`, section `rotation`:

| Key | Default | What it does |
|---|---|---|
| `invertMouseX` | `false` | The rotate drag moves the near side the way the mouse goes (mouse left: near side left). `true` is VMod's own sense. Applies to ships too. |
| `invertMouseY` | `false` | The same for up and down (mouse up: near side up, the top tips away). `true` is VMod's own sense. |
| `returnTicks` | `10` | How long a let-go or unfrozen entity takes to roll back upright, in ticks. `0` snaps. |

## Building and testing

```
./gradlew build                        # the release jar: build/libs/physgununbound-<version>.jar
./gradlew runGameTestServer            # 17 gametests of the hold itself, no VMod needed
./gradlew runGameTestServer -Pvmod     # the same plus 2 with the real VMod, VS, Kotlin for Forge and Architectury
```

- **Default suite (`src/main/java/.../gametest`):**
  - a held mob follows the target and swings round with the holder;
  - letting go throws it with the hold's speed and clears every claim;
  - the wheel and the rotate drag;
  - the rotate drag tips the whole model: the orientation, the heading kept, no head pitch, the grabbed point turned with it; a frozen entity keeps the pose and a hold out of the freeze or from a second holder carries on from it; an arrow's own heading and pitch follow the hold;
  - walls stop it;
  - items, a cart on a rail, a boat, falling sand and an armor stand are held (and survive it);
  - an arrow, a snowball and a fireball in flight are held still and fly on when let go; a stuck arrow and a fishing bobber are refused;
  - a held player is steered and claimed;
  - the refusals (self, spectator, item frame, interaction box, another mod's claim on every aspect, a minigame's rule), and that a claim on motion alone doesn't refuse;
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
  - `ModelPose`: what of an orientation an entity's own renderer shows, the model rotation left for clients to draw, and a frozen entity's kept pose.
  - `Grabbing`: the ray and the refusals.
  - `Freezing`, `HeldEntities`: the claims and the move stop.
  - `HoldEvents`: the Forge events.
  - `PhysgunInput`: one physgun message.
- `vmod/`: the bridge to VMod. `Vmod` is the only door. `VmodBridgeImpl` and `VmodPackets` name VMod's classes and only load with VMod.
- `mixin/`, `mixin/vmod/`: the three hooks, in two configs.
- `network/`: the beam message (with the held entity's orientation), the frozen-pose message and the held-player message (Camafy channel `physgununbound:main`, protocol "2", optional on both sides: server and client with the mod need the same version).
- `client/`: the beam (drawn like VMod's `PhysgunRayRenderer`), the turned models of held and frozen entities, and the held player's side of a hold.

## Known limits

- The turn is drawn, not physical: a held entity's hitbox stays upright and axis-aligned however its model is tipped, so a tipped cow pushed into a wall stops where its upright box does. Its own animations (a walking leg, a turning head) play on the turned model.
- A held vehicle a player drives (a boat, a horse) is moved by that player's client, which applies its own boat physics on top, so it hangs a little lower than a mob would. Players and driven vehicles aren't turned.
- A modded projectile that moves itself outside the vanilla projectile ticks (its own `tick` without calling the base one) keeps flying while held; tag it ungrabbable.
- Without this mod on a held player's client, the hold works from positions that arrive a ping late, so it can wobble on a laggy connection.
