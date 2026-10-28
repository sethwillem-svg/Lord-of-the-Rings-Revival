# Playtest: world scale (half vs quarter)

Branch: `world/world-scale` (PR into `integrate/wave-g5`, do not merge to `main`).
Goal: pick the scale for Middle-earth. Make one **half** world and one **quarter** world, walk the same route in
both, and decide. A **Legacy** world (today's size) is there for comparison.

```sh
git fetch origin && git switch world/world-scale
./gradlew runClient
```

## How a world gets its scale

The scale is chosen once, when the world is created, and stays with that world. Changing it later would move every
coast, road and waypoint, so old chunks would no longer line up with new ones.

| Scale | One map pixel | How to create it |
| --- | --- | --- |
| **Half** (default) | 32 blocks | Nothing to do: just create a new world |
| **Quarter** | 16 blocks | Create World, **More**, **Data Packs**, move "Middle-earth: Quarter scale (1/4)" to the right (Selected), Done, Create |
| Legacy (old size) | 64 blocks | Same, with "Middle-earth: Legacy scale (1x)" |

Enable at most one world-scale pack. Dedicated server: before the world is first created, add the pack to
`server.properties`, e.g. `initial-enabled-packs=vanilla,mod/lotrrevival:datapacks/world_scale_quarter`.

Check which scale a world runs at with **`/lotr worldscale`** (anyone can run it). It prints the scale, blocks per
map pixel, Hobbiton to Bree in blocks, and the scale the world was made at.

Existing worlds from before this branch were Legacy. Opened on this branch they would load at half, so operators get
a red warning on login and in `/lotr worldscale`. Fix: `/datapack enable "mod/lotrrevival:datapacks/world_scale_legacy"`,
then save and reopen the world. Or just use new worlds for this test.

## Expected numbers (straight line from Hobbiton)

| | Legacy | Half | Quarter |
| --- | --- | --- | --- |
| Brandywine Bridge | 4,902 | 2,451 | 1,226 |
| Bree | 12,774 | 6,387 | 3,193 |
| Michel Delving | 7,707 | 3,853 | 1,927 |
| Hobbiton to Bree, sprinting (5.6 blocks/s) | about 38 min | about 19 min | about 9.5 min |
| Hobbiton to Bree, walking (4.3 blocks/s) | about 49 min | about 25 min | about 12 min |
| Landing point (block x, z) | 192, 0 | 96, 0 | 48, 0 |
| Bree-hill top (painted y, before small-hill noise) | 97 | about 87 | about 80 |

Heights: the map is squashed horizontally by 1/2 or 1/4, and painted heights are pulled toward sea level by 0.7
(half) or 0.5 (quarter). Painted slopes therefore get about 1.4x (half) or 2x (quarter) steeper, not 2x or 4x, and
mountains keep most of their height. Small bumps (detail noise) are the same at every scale.

## Setup (each world)

New singleplayer world, Creative, **Allow cheats: ON**, then:

```
/lotrrevival middleearth
/lotr worldscale
/lotr waypoint unlock @s all
/lotr waypoint list
```

`/lotr waypoint list` now prints each waypoint's block x and z and how far away it is. Write down Hobbiton,
Brandywine Bridge, Bree and Michel Delving for each world.

## Checks

Do every check in the half world and in the quarter world. Note the scale next to any failure.

### Scale and coordinates

- [ ] `/lotr worldscale` says Half in the default world and Quarter in the quarter-pack world.
- [ ] `/lotr waypoint list` coordinates match the table above (within a few blocks): Hobbiton 0, 0; Bree about
  x 6,384 / z 192 (half) or x 3,192 / z 96 (quarter).
- [ ] Arriving through `/lotrrevival middleearth` puts you next to Hobbiton, in the Shire, near the East Road.
- [ ] The ring portal (throw a gold ring into fire in the overworld) lands you in the same place.
- [ ] `/lotr waypoint travel bree` puts you in Bree village land at the coordinates the list printed.

### The walk: Hobbiton to Bree on the East Road

Start a timer at Hobbiton, follow the East Road east, sprinting on foot (or ride a horse and note it).

- [ ] Time to the Brandywine Bridge: ____ (half) ____ (quarter).
- [ ] Time to Bree: ____ (half) ____ (quarter). Does that feel right for "a few days' walk" in game terms?
- [ ] The road is continuous the whole way: no gaps, no jumps sideways, no sudden ends.
- [ ] The road meets the Brandywine at the bridge waypoint and crosses on a bridge; the bridge spans the river
  (river is narrower at quarter scale, that is expected).
- [ ] The road runs through Bywater, Frogmorton and Whitfurrows waypoints and arrives in Bree.
- [ ] Also walk west from Hobbiton to Michel Delving: ____ (half) ____ (quarter).

### Terrain

- [ ] Hills: the Shire's rolling hills look natural, not like spikes or cliffs. Note any slope that feels too steep.
- [ ] Bree-hill stands clearly above Bree-land (top about y 87 half, y 80 quarter).
- [ ] The White Downs, Tower Hills and the Ered Luin foothills (far west) still read as high ground. Do the Ered
  Luin foothills still feel like the edge of mountains?
- [ ] Coasts (Gulf of Lune west of the Tower Hills) and Lake Evendim (north) look like smooth shores, not stair-steps.
- [ ] Biome borders (F3) follow the map: Old Forest east of the Brandywine, Barrow-downs south-east of it, Chetwood
  north-east of Bree, Midgewater east of Bree.
- [ ] The Old Forest and the Shire woodlands feel big enough to get lost in (quarter shrinks them most).

### Spacing of places

Villages are not generated yet, so judge spacing by the waypoints (each is a settlement or landmark).

- [ ] Shire villages (Hobbiton, Bywater, Frogmorton, Waymeet) are far enough apart that each feels like its own
  place, but close enough that walking between them is fun. Half: ____ Quarter: ____
- [ ] Bree, Staddle, Combe and Archet feel like neighbouring villages, not one town.

### Respawn and fast travel

- [ ] Die in Middle-earth far from any bed: you respawn within about 250-750 blocks (half) or 125-375 blocks
  (quarter) of where you died, on dry land.
- [ ] Fast travel still works to every unlocked waypoint and arrives on solid ground.

### Verdict

- [ ] Preferred scale: **Half** / **Quarter** (circle one), and why: ______________________
- [ ] If neither is right: suggest a number (e.g. 1/3) and whether hills should be flatter or taller.
