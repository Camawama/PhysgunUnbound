# Round 10 (2026-10-30), agent `physgun`

The owner's report: the server froze for good the moment they grabbed (pressed R by) a physgun-frozen ship 1300 blocks
up in the main pack, and the save ("FLAT AFTER CRASH") froze again on every join.

## Done
- The freeze: a deadlock between VMod 1.9.1's netty-thread R ray-cast (waiting for an unloaded shipyard chunk under
  VMod's per-player lock) and the server thread taking that lock at tick START (Physgun Unbound's
  `letGoOfMissingShips`, then VMod's own tick). Fixed: `vmod/VmodMessages` + `mixin/vmod/PhysgunMessageThreadMixin`
  hand VMod's physgun messages to the server thread (`[vmod] messagesOnServerThread`, on); `VmodBridgeImpl` never
  takes VMod's lock on the server thread. Gametests `-Pvmod` 28/28 (`VmodThreadTests`).
- Harness `physgun_deadlock` (main pack): new jar passes (61/61 messages on the server thread, 53 ms max gap); old jar,
  switch off and `--without physgununbound` deadlock (VMod alone too).
- Release jar in JARS (04:24, `build-mods.ps1 -Mods physgununbound`).
- The rejoin: client render thread stuck in Full Stop's `ShipHullSweep.resolve` -> `BlockCollisions` ->
  `ClientChunkCache.getChunk` (VS's `preGetChunk`, the only `preGetChunk` among the pack's ClientChunkCache mixins).
  Full Stop guard: `ShipHullSweep.MAX_MOVE` (100 blocks); a longer or non-finite move isn't swept or collided, the body
  stays and loses its velocity. Gametests `-PshipTests -PtestOnly=ImplausibleMoveTests,ShipBodyTests`: 21/21.
  Full Stop CHANGELOG entry quoting the owner.
- Harness `owner_physgun_join` watchdog: dumps every 2 s from 10 s stalled; `LocalMoveWatchMixin`/`MoveWatch` log the
  local player's moves. CLAUDE.md bullets for both scenarios.

- Full Stop apparent gravity (coordinator, from agent space): `ShipTracker` keeps a smoothed ship acceleration (timed by
  the frame's own motion), `ShipApparentGravity` scales standing/seated weight by (g - a.down)/g in [0, 3], switch
  `crewWeighByApparentGravity`; `ShipWeightHandler.lastWeight`. Gametests `ShipFallWeightTests` (on: 128 N avg = the
  measured 8.1 m/s^2 fall, 0.036 rad/s, 2.4 deg; off: 736 N, 0.181 rad/s, 9.2 deg); with ShipSeatWeight, ShipBody,
  ImplausibleMove 24/24. CHANGELOG entries. Final jar `<scratchpad>/jars/physgun-fullstop-2.0.0-FINAL.jar`.

## Final (05:50)
- Rejoin root cause confirmed by the harness stack (join_diag): Full Stop `PhysicsDispatchServer.onMount` handed the
  player a never-ticked Create seat's `pos - xOld` (its shipyard position) via `Gravity.setWorldVelocity`, from
  `LocalPlayer.stopRiding` at the join. Fixed in Full Stop (`observedVehicleMotion`, seed refusal, `ShipHullSweep`
  100-block guard). join_root: the owner's save joins and ticks a minute, longest local move 0.0, 0 refused moves,
  player stays at y -25.21, longest tick gap 285 ms.
- Full Stop free-fall crew weight (`crewWeighByApparentGravity`): launch before 524-571 N m from the first tick and
  48.5 deg tilt by y 1008; after 11-16 N m and 0.1 deg by 40 m/s (fast aerodynamics above y 962 remain, both alike).
- Physgun Unbound and Full Stop are in JARS (Full Stop built by the coordinator 05:47). Nothing left open in my area.
