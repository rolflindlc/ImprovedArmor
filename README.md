# Improved Armor

A Minecraft Forge **1.20.1** mod that replaces vanilla's armor curve with a flat one, so that
high armor stops being a "mini god mode" and every armor point is worth the same amount of
effective health.

> Status: work in progress (`0.1.0`). See the [roadmap](#roadmap).

## Why

In vanilla, damage reduction is `min(20, max(armor/5, armor - damage/(2 + toughness/4))) / 25`,
and armor, the Resistance effect and Protection enchantments are applied one after the other
(each one multiplies the previous). Effective health therefore grows *convexly*: the last armor
points are worth far more than the first ones, and a fully enchanted diamond/netherite set
takes almost no damage.

## How it works

All damage that vanilla reduces with armor goes through this formula instead, in two steps:

```
# 1. General defense (works against every hit)
A_gen     = armor * (1 - attacker's armor penetration)
          + 1.5 * min(general EPF, 20)  # Protection
          + 8   * resistance level      # Resistance I = 1, II = 2, ...
A_gen     = max(A_gen / 5, A_gen - damage / (2 + toughness / 4))   # big-hit penalty (same term as vanilla)
damage'   = damage * (1 - A_gen / (A_gen + K))                      # K = 18 by default

# 2. Specific defense (only against that damage type), on what is left
A_spec    = 1.5 * min(specific EPF, 20) # Fire / Blast / Projectile Protection, Feather Falling...
damage''  = damage' * (1 - A_spec / (A_spec + K))
```

* There is **no cap of 20**: the effective health of each step is `1 + A / K`, so every armor point adds the same
  amount of it. Only the two steps multiply.
* **Armor penetration** (`improvedarmor:armor_penetration`, 0 to 1) ignores that fraction of the target's armor
  points; Protection and Resistance still count in full. Nothing has it by default: other mods, commands and the API
  give it.
* **Toughness** keeps its vanilla meaning: it softens the big-hit penalty.
* **Resistance V (or higher)** still makes the entity immune, as in vanilla.
* Damage types tagged `bypasses_armor`, `bypasses_resistance` and `bypasses_enchantments`
  are still ignored by the matching layer. Absorption and armor durability are untouched.
* **Non-player entities** (mobs, bosses) have a configurable cap on their effective armor
  (20 by default), so heavily armored bosses do not become much tougher than they are today.

The mod only changes the formula, not the items: any armor from any other mod that uses the
`generic.armor` / `generic.armor_toughness` attributes works out of the box.

## Compatibility

* Required on the **server**; the HUD is client-only and optional.
* Standalone. [Rolflin's Rites & Paths](https://github.com/rolflindlc/RPGClasses) detects it
  through the public API below and routes its "ignore part of the armor" maths through the new curve
  only when this mod is loaded.

## Public API

`io.github.rolflindlc.improvedarmor.api.ImprovedArmorApi` (static methods, `API_VERSION = 1`):

| Method | Meaning |
| --- | --- |
| `isActive()` | The flat curve is on (server config master switch). |
| `k()` | Curve constant K. |
| `damageAfterArmor(entity, source, damage[, armorScale])` | Damage left after armor, Resistance and Protection. `armorScale` multiplies the armor attribute to simulate armor piercing. |
| `effectiveArmor(entity, source)` | General armor points (armor, Protection, Resistance) against that damage source. |
| `specificArmor(entity, source)` | Specific armor points (only against that damage type), applied after the general ones. |
| `armorPenetrationAttribute()` / `armorPenetration(entity)` | The penetration attribute and an entity's current value. |
| `reduction(entity, source, damage)` | Reduction in [0, 1) for a hit of that size. |
| `armorForReduction(r)` | `K * r / (1 - r)`. |

Call it only when `improvedarmor` is loaded, preferably through reflection, so your mod keeps working
without it.

## Building

```
./gradlew build
```

Requires Java 17. The formula lives in `core/` as plain Java with unit tests, so
`./gradlew test` does not need Minecraft.

## Armor HUD (client)

With the mod installed on the client, the armor bar shows the **effective armor against a normal hit** (armor,
Protection and Resistance through the flat curve), and every point shows where it comes from:

| Icon | Source | Color |
| --- | --- | --- |
| Armor | each worn piece | average of its texture without the outline (one shade per slot; dye for leather) |
| Armor (white) | other armor modifiers (classes, other mods) | white |
| Amulet with gold rim | accessories in Curios slots (optional) | average of the item texture |
| Enchanted book (moving shine) | Protection-type enchantments | one per enchantment |
| Potion bottle | Resistance and effects that add armor | the effect's color |

Up to 3 rows of 20 points are stacked; past that the bar collapses into one proportional row with the total next to it.

Above the bar, small icons show the protections that only work against one kind of damage, with the **extra armor
points** they are worth, highest first, 4 per row: fire, explosions, projectiles and falls (enchantments), and
Iron's Spellbooks general magic and every school (their percentage resistances converted to the armor points that
give the same reduction). They apply after the general defense, on the damage that is left.

Armor pieces show in their tooltip how much of an ordinary hit they take away for you (or how your reduction would
change if you put them on); with Shift, their armor, toughness, enchantments and specific defenses.

## Command

`/improvedarmor [target] [damage]`: breakdown of an entity's armor (pieces, toughness, Protection, Resistance,
effective armor, reduction of an example hit and specific defenses). Your own needs no permission; another entity
needs level 2.

`config/improvedarmor-client.toml`: `enabled`, `maxRows`, `showNumber`, `tooltips` and `colorOverrides`
(`"minecraft:iron_chestplate=#D8D8D8"`, `"minecraft:protection=#B57CFF"`, `"minecraft:resistance=#9146F0"`).
The icons are in `assets/improvedarmor/textures/gui/hud_icons.png` (layout in `tools/hud_icons.py`), so a resource
pack can redraw them.

## Roadmap

1. Project skeleton and CI. *(done)*
2. Armor formula (pure Java) with unit tests. *(done)*
3. Mixins and server config. *(done)*
4. Public API and `/improvedarmor` breakdown command. *(done)*
5. Client HUD: extra rows coloured by armor source. *(done)*
6. Item tooltips. *(done)*
7. Specific defense after the general one, armor penetration. *(done)*

## License

[MIT](LICENSE)
