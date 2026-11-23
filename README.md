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

All damage that vanilla reduces with armor goes through this formula instead:

```
A_eff     = armor
          + 1.5 * min(EPF, 20)          # enchantment protection factor that applies to the damage type
          + 5   * resistance level      # Resistance I = 1, II = 2, ...
A_eff     = max(A_eff / 5, A_eff - damage / (2 + toughness / 4))   # big-hit penalty (same term as vanilla)
reduction = A_eff / (A_eff + K)         # K = 22 by default
```

* There is **no cap of 20** and **no multiplicative stacking**: effective health is
  `1 + A_eff / K`, so every armor point adds the same amount of it.
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
| `effectiveArmor(entity, source)` | Armor points from every source that apply against that damage source. |
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
Protection and Resistance through the flat curve), and every point is tinted by where it comes from: each worn piece
(the average color of its texture, one shade per slot, the dye for leather), other armor modifiers (white), each
Protection-type enchantment and the Resistance effect (its potion color). Up to 3 rows of 20 points are stacked; past
that the bar collapses into one proportional row with the total next to it.

`config/improvedarmor-client.toml`: `enabled`, `maxRows`, `showNumber` and `colorOverrides`
(`"minecraft:iron_chestplate=#D8D8D8"`, `"minecraft:protection=#B57CFF"`, `"minecraft:resistance=#9146F0"`).

## Roadmap

1. Project skeleton and CI. *(done)*
2. Armor formula (pure Java) with unit tests. *(done)*
3. Mixins and server config. *(done)*
4. Public API *(done)* and `/improvedarmor` breakdown command.
5. Client HUD: extra rows coloured by armor source. *(done)*
6. Item tooltips.

## License

[MIT](LICENSE)
