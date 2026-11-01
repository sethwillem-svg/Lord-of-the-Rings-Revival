Held-spear model overrides (written for this add-on, not copied from Middle-earth).

In Middle-earth's `neoforge-fixes` branch the held models of these 11 spears point at `item/<spear>.png`, which does not
exist (only `item/<spear>_inventory.png` does), so they render purple in hand. These files use the same parent model and
point at the inventory texture the mod already ships. Delete a file once Middle-earth ships the held texture for it.
