Held-spear model overrides (written for this add-on, not copied from Middle-earth).

In Middle-earth's `neoforge-fixes` branch the held models of these 11 spears point at `item/<spear>.png`, which does not
exist (only `item/<spear>_inventory.png` does), so they render purple in hand. These files point at the inventory texture
the mod already ships, held like a normal handheld item: Middle-earth's spear parent scales the held model 2.5x for its
(missing) tall held texture, which made the small inventory icon huge. On our NPC renderer every spear is sized by
`client.render.WeaponGrip` anyway. Delete a file once Middle-earth ships the held texture for it.
