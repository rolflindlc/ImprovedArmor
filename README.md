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

* Required on the **server**; the (planned) HUD is client-only and optional.
* Standalone. [Rolflin's Rites & Paths](https://github.com/rolflindlc/RPGClasses) detects it
  through a tiny public API (planned) and rescales its own armor values only when it is loaded.

## Building

```
./gradlew build
```

Requires Java 17. The formula lives in `core/` as plain Java with unit tests, so
`./gradlew test` does not need Minecraft.

## Roadmap

1. Project skeleton and CI.
2. Armor formula (pure Java) with unit tests.
3. Mixins and server config.
4. Public API and `/improvedarmor` breakdown command.
5. Client HUD: extra rows coloured by armor source.
6. Item tooltips.

## License

[MIT](LICENSE)
