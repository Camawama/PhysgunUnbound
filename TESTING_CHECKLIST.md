# Physgun Unbound: in-game testing checklist

The gametests (`./gradlew runGameTestServer`, and with `-Pvmod` against the real VMod and a real Valkyrien Skies raft) cover the server side: grabbing, following, the wheel, rotating, walls, throwing, freezing, the refusals and the cleanup. Everything below needs a real client: how it looks and feels, multiplayer, and the other mods in the pack.

Tick each box as you go. Next to anything that looks wrong, write what you saw.

**Setup:** give yourself VMod's physgun (`/give @s the_vmod:physgun`). The server log should say `VMod 1.9.1 found: its physgun picks up entities too` at start, and nothing from Physgun Unbound after it.

## 1. Holding
- [ ] Aim at a cow and hold the left button.
  - It is picked up where you aimed (its back, its head).
  - It stays at that distance and follows the crosshair smoothly as you look and walk around.
  - A blue beam runs from the gun to the point you grabbed and bends as the cow lags behind.
- [ ] Look at the cow in third person (F5), and have a second player watch. The beam comes from your eyes and ends on the cow for both of you.
- [ ] Turn around on the spot. The cow swings round you and keeps facing the same way relative to you.
- [ ] Wheel up and down. The cow goes out and comes in about a block a notch. It stops about a block from your eyes, and at 128 blocks.
- [ ] Hold E and move the mouse. Your view stays still and the whole cow turns: mouse left moves the side facing you to the left, mouse right to the right; mouse up moves that side up (its top tips away from you), mouse down moves it down. Keep dragging and it rolls right over, upside down, on its side, nose up, any way at all, always about its middle. Let go of E: looking works again.
  - Its legs still walk and its head still looks about while it is tipped (the animations play on the turned model).
  - Its hitbox (F3+B) stays upright and axis-aligned however it is tipped; its shadow stays flat on the ground but follows the turn: on its side the blob stretches along the way it lies (as Gravity Unbound's does for a body in another gravity), and rolls back round as it rights itself; its name tag (name it) stays upright.
  - Push the tipped cow against a wall: it stops where its upright box does (the turn is drawn, not physical).
- [ ] Look up and down while holding the cow. It tips with your view, as a held ship does. Turn round: it keeps its orientation relative to you. (Set `followViewPitch` false in the server config to keep entities level when you look up and down.)
- [ ] Tip the cow upside down and let go. It rolls back upright over about half a second, smoothly, from where it was (no snap), then walks off. Set `rotation.returnTicks` to 0 in the client config: it snaps instead.
- [ ] Have a second player watch while you tip the cow: they see the same turn, smoothly, and the same roll back when you let go.
- [ ] Pick up a ship with the physgun and hold E: mouse left moves its near side left and mouse up moves it up (the top tips away), the same feel as the cow (`rotation.invertMouseX` / `invertMouseY` true in the client config bring VMod's old sense back, per axis, for both).
- [ ] Pick up an item on the ground, a boat and a minecart, and tip each one over with E. Each turns about its middle and rolls back when let go.
- [ ] Push the cow into a wall and into the floor. It stops at them and doesn't pass through or jitter.
- [ ] Swing quickly and let go. The cow flies off the way you swung and lands (Full Stop may hurt it if it hits a wall hard).
- [ ] While holding the cow, switch the hotbar slot away from the physgun. The cow drops, keeping its speed.

- [ ] Grab a thrown egg or snowball in flight and hold E: the sprite keeps facing you whatever you do with the mouse (it never turns edge-on); its shadow on the ground still turns. A dropped item and an experience orb likewise.

### In the Great Unknown (VS Genesis, everything at 1/16)
- [ ] Grab a pig (or a cow) a few of your own blocks away. It stays where you grabbed it; it doesn't jump out in front of you.
- [ ] Wheel up and down. It moves about one of your own (tiny) blocks a notch, as a cow does a block a notch at full size.
- [ ] Grab a ship there and wheel: the ship also moves about one of your blocks a notch.
- [ ] Move the mouse a little while holding and let go: the pig drifts off gently, about as fast (in your own blocks) as the same flick throws a cow at full size, not flung across the room.
- [ ] Swing hard and let go: a real throw, still in proportion to your size.
- [ ] Look at the beam while holding the pig, and while holding a ship: it is a thin line leaving your hand, as thin against the tiny world as it is at full size, not a huge tube from off to the side. A second player at 1/16 sees your beam thin too.

## 2. Freezing
- [ ] Hold a zombie in the air and right click. It stays exactly there, doesn't move, attack or fall, and you are no longer holding it.
- [ ] Throw a Loyalty trident at a mob, grab it as it flies back, freeze it. It hangs perfectly still (no creeping, no stutter) on your screen and on a second player's; R brings it back to you. A trident without Loyalty, an arrow and a snowball frozen in flight sit still the same way.
- [ ] Tip a zombie on its side with E and freeze it. It hangs on its side. Leave and come back (or restart): still on its side. Aim at it and press R: it rolls upright and falls. Freeze it on its side again, then grab it: it stays on its side in the hold (no snap) and turns on from there.
- [ ] Middle click does the same.
- [ ] Leave the world and come back (or restart the server). The zombie is still frozen in the same spot.
- [ ] Aim at it and press R (left button up). It falls and walks again.
- [ ] Freeze it again, then grab it: it is unfrozen and held.
- [ ] Press R on a mob you froze with `/data merge entity @e[type=villager,limit=1,sort=nearest] {NoAI:1b}` (not the physgun). Nothing changes.

## 3. Things to pick up
- [ ] **An item** on the ground: it lifts and floats with no falling flicker, and you don't pick it up when you pull it right in. Let go: it drops and you can pick it up after half a second.
- [ ] **A minecart on a rail:** it comes off the rail. Let go on the rail: it rolls on the rail again.
- [ ] **A boat** on land and on water.
- [ ] **Sand:** break the block under a sand block and grab the falling sand. Hold it for over 30 seconds: it stays an entity. Let go over the ground: it lands as a block.
- [ ] **Primed TNT:** it still explodes on time while held.
- [ ] **An armor stand, and an experience orb.**
- [ ] **A chicken jockey (or a zombie on a horse):** aiming at the rider lifts both.
- [ ] **Projectiles in flight:** shoot an arrow up and grab it before it lands: it stops dead where you hold it, doesn't fall, and doesn't hit you when pulled in. Tip it with E: it points the way it is turned. Swing and let go: it flies off along the throw and sticks where it lands. The same with a thrown snowball or egg (they don't break while held), an ender pearl (it doesn't teleport you while held), a blaze's fireball or a ghast's (held still, then flies on and explodes where it lands), a wither skull, a shulker bullet, a firework rocket. A trident too.
- [ ] **An Automobility car** (and other modded entities): it is picked up like a boat. Let go in the air: it drops (MKW's driving takes over as it lands). Note whether MKW still steers it while held (it shouldn't move by itself).
- [ ] **Things it shouldn't pick up** (the physgun goes on seeking, or grabs a ship behind them):
  - an item frame, a painting, a lead knot;
  - an arrow stuck in a wall;
  - a fishing bobber (cast a rod and aim at the bobber), an eye of ender in flight;
  - the ender dragon;
  - a Create train or bearing contraption;
  - a sleeping villager.

## 4. Ships (Valkyrien Skies)
- [ ] Build a small boat ship with a pig standing on its deck. Aim at the pig: the pig is picked up, not the ship.
- [ ] Aim at the ship's hull with nothing in front: VMod picks up the ship, exactly as without this mod (distance, rotate, freeze, R).
- [ ] Hold a pig and push it against a ship's hull. It collides with the hull.
- [ ] Put the pig down on a moving ship's deck and let go. It rides the ship as usual.
- [ ] Stand on a moving ship and grab a mob on the ground. It is held steadily.

## 5. Players (two clients)
- [ ] Player A holds player B in the air.
  - B floats smoothly (no rubber-banding) and can look around but not walk away.
  - After 10 seconds in the air, B is not kicked for flying (test on a server with `allow-flight=false`).
- [ ] A lets go while swinging: B is thrown, falls, and takes fall or crash damage as usual. No fall damage from the time B was held.
- [ ] A right clicks while holding B: B is just let go, not frozen.
- [ ] B logs out while held: A's beam disappears and nothing errors. A logs out while holding B: B drops.
- [ ] B dies while held (A holds B over lava). The hold ends and A's physgun works again.
- [ ] B is in a Game Start match with PvP off, or protected: A can't pick B up.
- [ ] B has Physgun Unbound removed from their client (VMod still installed): B can still join, A can still hold B (a little laggier), and B sees no beam.
- [ ] Set `grabPlayers = false`: A can't pick up B, or a horse B rides.

## 6. Other mods
- [ ] **Gravity Unbound:** stand next to plating that makes a sideways or inverted field and grab a cow outside it. Carry it into the field: it follows the crosshair exactly, no drift along the field's "down", no fight (it isn't yanked toward the plates while you hold it). Let go inside the field: it flies the way you swung it, then the field's gravity takes it. Freeze it inside the field: it hangs still. Grab a cow that is standing on the plating: it comes off and follows you.
- [ ] **Full Stop:**
  - a held mob slammed into walls takes no crash damage while held;
  - a thrown one hitting a wall does;
  - a held player gets no g-force effects.
- [ ] **Gravity Unbound:**
  - a player standing on gravity plating can't be picked up;
  - a mob held in a rotated field moves as held, and falls along the field's gravity when let go.
- [ ] **MKW Mobility:** a kart, or a driver in one, can't be picked up.
- [ ] **Grapple Unbound:** a player swinging on a rope can't be picked up. Grappling while held does nothing odd.
- [ ] **WallJump:** a held player doesn't cling to walls.
- [ ] **Pehkui (Genesis):** a shrunk pig follows the crosshair at normal speed.
- [ ] **Shaders (Oculus):** the beam still shows, with and without a shader pack.

## 7. Performance
- [ ] Hold a mob for a minute near a busy base: no tick lag in `/spark tps`, and nothing from Physgun Unbound in a spark profile.
