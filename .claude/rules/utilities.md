---
paths:
  - "src/main/java/first/demacia/utils/leds/**"
  - "src/main/java/first/demacia/utils/*.java"
---

# LEDs, `LookUpTable`, `RobotCommon`

## LEDs (`utils/leds`)

- Create exactly one `LedManager` (one `AddressableLED` on `LedConstants.PORT`, length `LedConstants.LENGTH`). Split it into named `LedStrip(name, size, manager[, offset])` segments.
- Strip API: `setColor(Color | Color[])`, `setBlink(...)`, `setGay()` (animated rainbow; rename to `setRainbow` when you touch it), `turnOff()`.
- `Color` is `org.wpilib.util.Color`.

## `LookUpTable`

- `new LookUpTable(columns)` or `new LookUpTable(double[][])`, then `add(key, v1, v2, ...)`. `get(key)` returns the other columns linearly interpolated, clamped outside the range. Rows stay sorted by key.

## `RobotCommon`

- Static `getIsRed()` / `setIsRed()` / `getIsComp()` / `setIsComp()`. Published as the `RC` tunable ("is red", "is comp" toggles). `isRed` starts **true**. `setIsComp` doesn't call `Log.removeInComp()`.

## `Data`

- Wraps a Phoenix 6 `StatusSignal` or a `Supplier`. `Data.refreshAll()` (called by `Log.periodic()`) refreshes everything once per loop. See `logging.md`.
