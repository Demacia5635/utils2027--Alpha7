# הלוג והדשבורד: 2026 מול 2027 (לפני ואחרי התשתית החדשה)

המסמך הזה משווה איך כותבים קוד שמציג ערכים בדשבורד, שומר לקובץ הלוג, מאפשר כיוונון מהדשבורד ומקפיץ התראות, בשלוש גרסאות:

| גרסה | איפה | מה היא |
|---|---|---|
| **2026** | `DemaciaCode-3`, ברנץ' `tashtiot` | WPILib 2026: `SmartDashboard`, `Sendable`, `Log` שלנו |
| **2027 לפני** | `utils2027--Alpha7`, ברנץ' `testing-telemetry` | WPILib 2027 alpha-7 אחרי ההעברה הראשונה: `Telemetry` ו-`Tunables` ישירות, לצד `Log` הישן |
| **2027 אחרי** | `utils2027--Alpha7`, ברנץ' `log-infrastructure` | התשתית החדשה: כל הלוג, הדשבורד והכיוונון עוברים דרך `Log` בלבד |

החלק הראשון מסביר מה השתנה ולמה. החלק השני עובר מקרה אחרי מקרה עם קוד מקביל לשלוש הגרסאות. בסוף יש טבלת המרה מהירה ומלכודות שכדאי להכיר.

---

## 1. התמונה הכללית

### מה השתנה ב-WPILib 2027

- **`SmartDashboard`, `Sendable` ו-`SendableChooser` נמחקו.** במקומם WPILib נתן שני APIs נפרדים:
  - **`Telemetry`** — לערכים שרק **מציגים** (פלט בלבד). אין לו עדכון אוטומטי: מי שרוצה ערך מתעדכן קורא ל-`Telemetry.log` בכל לולאה.
  - **`Tunables`** — לערכים ש**עורכים** מהדשבורד. `SendableChooser` הפך ל-`Selectable`, ו-`Sendable` עם setter הפך ל-`ComplexTunable`.
- **כל הקבועים ב-ALL_CAPS**, ו-`Alert` דורש `id` ייחודי ורמות `LOW/MEDIUM/HIGH`.
- **ב-alpha-7 עצמו יש שינויים בין גרסאות**, כך שקוד שמשתמש ב-WPILib ישירות נשבר בכל עדכון. זו הסיבה העיקרית לעטוף הכל ב-`Log`.

### לאן הנתונים הולכים

| | 2026 | 2027 לפני | 2027 אחרי |
|---|---|---|---|
| ערכים פשוטים (`Log.putData`) | NT `/Log/...` + קובץ | NT `/Log/...` + קובץ | NT `/Telemetry/...` + קובץ `Telemetry/...` |
| אובייקט שלם (מנוע, חיישן) | NT `/SmartDashboard/...` בלבד | NT `/Telemetry/...` בלבד | NT `/Telemetry/...` + קובץ `Telemetry/...` |
| ערכים שעורכים מהדשבורד | NT `/SmartDashboard/...` בלבד | NT `/Tunables/...` בלבד | NT `/Tunables/...`, וכל עריכה נרשמת בקובץ `Tunables/...` |
| אותות מנוע ל-SysId | מערך מקובץ `"Float/name: Position, ... \| ..."` | אותו מערך מקובץ | ערך נפרד לכל אות: `Telemetry/motors/<שם>/Position` וכו' |
| הודעות `Log.log` | קובץ + Alerts | קובץ + Alerts | קובץ + Alerts |

**הבעיה המרכזית שנפתרה:** גם ב-2026 וגם ב-2027 לפני, מה שמוצג על אובייקטים (מנועים, חיישנים, מנגנונים) **לא נשמר בקובץ הלוג**, כי הקלטת NT לקובץ כבויה. אחרי השינוי כל מה שמוצג בדשבורד נשמר גם בקובץ, כולל כל שינוי שעשו מהדשבורד.

### כמה APIs צריך להכיר

| | 2026 | 2027 לפני | 2027 אחרי |
|---|---|---|---|
| מחלקות שתלמיד צריך להכיר | `Log`, `SmartDashboard`, `Sendable`/`SendableBuilder`, `SendableChooser`, `ConsoleAlert` | `Log`, `Telemetry`, `TelemetryLoggable`/`TelemetryTable`, `Tunables`, `TunableBoolean`, `ComplexTunable`/`TunableTable`, `Selectable`, `Trigger` | `Log`, ומעט טיפוסים שלנו: `Loggable`/`LogTable`, `TunableGroup`/`TuneTable`, `Chooser` |
| imports מ-WPILib ללוג מחוץ לחבילת `log` | הרבה | ~19 קבצים | **אפס** |

---

## 2. מקרה אחרי מקרה

### 2.1 להציג ערך שמשתנה (ולשמור אותו בקובץ)

**2026**
```java
Log.putData("chassis/gyro angle", () -> getGyroAngle().getDegrees());
```

**2027 לפני** — אותו קוד, אותה התנהגות (NT `/Log` + קובץ).

**2027 אחרי** — אותו קוד, עכשיו ב-`/Telemetry`:
```java
Log.putData("chassis/gyro angle", () -> getGyroAngle().getDegrees());
// ערך שמעניין רק בבדיקות: נשמר בקובץ תמיד, ונעלם מהדשבורד כשמסמנים "is comp"
Log.putData("arm/debug error", this::getError, Log.Dashboard.DEBUG);
```

### 2.2 ערך שקובעים פעם אחת (סטטוס, הודעה לדשבורד)

**2026**
```java
SmartDashboard.putString("elastic/Status", "Saved at: " + path);
```

**2027 לפני**
```java
Telemetry.log("elastic/Status", "Saved at: " + path);
```

**2027 אחרי**
```java
Log.put("elastic/Status", "Saved at: " + path);
```

### 2.3 אובייקט עם הרבה ערכים (מנוע, חיישן, מנגנון)

**2026** — מממשים `Sendable` ונרשמים ל-SmartDashboard (רק לדשבורד, לא לקובץ):
```java
SmartDashboard.putData("motors/" + name, this);

@Override
public void initSendable(SendableBuilder builder) {
  builder.addDoubleProperty("Position", this::getCurrentPosition, null);
  builder.addDoubleProperty("Voltage", this::getCurrentVoltage, null);
}
```

**2027 לפני** — מממשים `TelemetryLoggable` של WPILib (רק לדשבורד, לא לקובץ):
```java
Log.publishTelemetry("motors/" + name, this);

@Override
public void logTo(TelemetryTable table) {
  table.log("Position", getCurrentPosition());
  table.log("Voltage", getCurrentVoltage());
}
```

**2027 אחרי** — מממשים `Loggable` שלנו (דשבורד **וגם** קובץ):
```java
Log.putData("motors/" + name, this);

@Override
public void log(LogTable table) {
  table.put("Position", getCurrentPosition());
  table.put("Voltage", getCurrentVoltage());
  table.recordEveryLoop("Position");   // רק לערכים ש-SysId צריך, ראו 2.10
  table.recordEveryLoop("Voltage");
}

@Override
public String logType() { return "Motor"; }
```

### 2.4 שמירה לקובץ בלבד, או רק מחוץ לתחרות

**2026 / 2027 לפני** — `LogLevel` עם ארבע רמות וקריאה מקובצת:
```java
Log.putData(name + ": Position, Velocity",
    new Data[] { positionSignal, velocitySignal },
    LogLevel.LOG_ONLY, "motors", false);
```

**2027 אחרי** — הכל תמיד נשמר בקובץ; בוחרים רק אם להציג בדשבורד תמיד או רק מחוץ לתחרות:
```java
Log.putData("motors/" + name, this);                        // תמיד בדשבורד
Log.putData("motors/" + name, this, Log.Dashboard.DEBUG);   // בדשבורד רק כש-is comp כבוי
```

### 2.5 מספר או דגל שעורכים מהדשבורד

**2026** — בתוך `initSendable`:
```java
builder.addDoubleProperty("test Value", this::getTestValue, this::setTestValue);
```

**2027 לפני** — `Tunables` ישירות (העריכה לא נרשמת בקובץ):
```java
Tunables.getTable("motors/" + name).publishDouble("test Value", this::getTestValue, this::setTestValue);
```

**2027 אחרי** — כל עריכה מהדשבורד נרשמת בקובץ תחת `Tunables/...`:
```java
Log.tune("motors/" + name + "/test Value", this::getTestValue, this::setTestValue);
Log.tune("arm/enabled", this::isEnabled, this::setEnabled);   // boolean
Log.tuneArray(name + "/Test Values", this::getTestValues, this::setTestValues);
```

### 2.6 קבוצת הגדרות (PID, Motion Magic, RobotCommon)

**2026**
```java
SmartDashboard.putData("motors/" + name + "/Motion Magic Config", new Sendable() {
  @Override
  public void initSendable(SendableBuilder builder) {
    builder.setSmartDashboardType("Motion Magic Config");
    builder.addDoubleProperty("Vel", () -> config.maxVelocity, v -> config.maxVelocity = v);
    builder.addBooleanProperty("Update", () -> cmd.isScheduled(), v -> { /* schedule/cancel ידני */ });
  }
});
```

**2027 לפני**
```java
Tunables.publish("motors/" + name + "/Motion Magic Config", new ComplexTunable() {
  @Override public String getTunableType() { return "Motion Magic Config"; }
  @Override public void publishTunable(TunableTable table) {
    table.publishDouble("Vel", () -> config.maxVelocity, v -> config.maxVelocity = v);
    table.publishBoolean("Update", () -> cmd.isScheduled(), v -> { /* schedule/cancel ידני */ });
  }
});
```

**2027 אחרי**
```java
Log.tune("motors/" + name + "/Motion Magic Config", "Motion Magic Config", t -> {
  t.number("Vel", () -> config.maxVelocity, v -> config.maxVelocity = v);
  t.flag("USE_KS", () -> flags[0], v -> flags[0] = v);
  t.button("Update", configMotionMagicCmd);   // כפתור אמיתי, בלי schedule/cancel ידני
});
```

### 2.7 כפתור בדשבורד שמריץ פקודה

**2026**
```java
SmartDashboard.putData("chassis/reset gyro", new InstantCommand(() -> setYaw(0)).ignoringDisable(true));
```

**2027 לפני** — דגל, `Trigger`, ואיפוס ידני (התבנית חזרה 11 פעמים בקוד):
```java
TunableBoolean resetGyro = Tunables.addBoolean("chassis/reset gyro", false);
new Trigger(resetGyro).onTrue(
    new InstantCommand(() -> setYaw(Rotation2d.ZERO)).ignoringDisable(true)
        .andThen(() -> resetGyro.set(false)));
```

**2027 אחרי**
```java
Log.button("chassis/reset gyro",
    new InstantCommand(() -> setYaw(Rotation2d.ZERO)).ignoringDisable(true));
```

### 2.8 בחירה מרשימה (Chooser)

**2026**
```java
SendableChooser<ControlMode> chooser = new SendableChooser<>();
chooser.setDefaultOption("DUTYCYCLE", ControlMode.DUTYCYCLE);
chooser.onChange(mode -> valueControlMode = mode);
SmartDashboard.putData("motors/" + name + "/Value Control Mode Chooser", chooser);
```

**2027 לפני**
```java
Selectable<ControlMode> chooser = new Selectable<>();
chooser.addDefault("DUTYCYCLE", ControlMode.DUTYCYCLE);
chooser.onChange(mode -> valueControlMode = mode);
Tunables.publish("motors/" + name + "/Value Control Mode Chooser", chooser);
```

**2027 אחרי** — הבחירה גם נרשמת בקובץ:
```java
Chooser<ControlMode> chooser = Log.chooser("motors/" + name + "/Value Control Mode Chooser");
chooser.addDefault("DUTYCYCLE", ControlMode.DUTYCYCLE);
chooser.add("VOLTAGE", ControlMode.VOLTAGE);
chooser.onChange(mode -> valueControlMode = mode);
```

### 2.9 מגרש (Field2d)

| 2026 | 2027 לפני | 2027 אחרי |
|---|---|---|
| `SmartDashboard.putData("chassis/field", field);` | `Tunables.publish("chassis/field", field);` | `Log.tune("chassis/field", field);` |

### 2.10 נתונים ל-SysId

**2026 / 2027 לפני** — מערך מקובץ אחד לכל המנועים, ש-`LogReader` מפרק לפי `|` ו-`:`. כל השורה נכתבת בכל לולאה, ולכן ארבעת האותות תמיד באותו אורך.

**2027 אחרי** — ערך נפרד לכל אות (`Telemetry/motors/<שם>/Position` וכו'). WPILib מדלג על ערכים שלא השתנו, אבל SysId מצמיד את ארבעת האותות **לפי אינדקס**, אז מנוע כותב אותם בכל לולאה:
```java
table.recordEveryLoop("Position");
table.recordEveryLoop("Velocity");
table.recordEveryLoop("Acceleration");
table.recordEveryLoop("Voltage");
```
`SysidCommand` ו-`SysidApp` מסננים לפי `Telemetry/motors/`. הקובץ הזה נקרא גם ב-AdvantageScope בלי כלים שלנו.

### 2.11 הודעה לקונסול ולקובץ

| 2026 | 2027 לפני | 2027 אחרי |
|---|---|---|
| `Log.log("arm ready");` | `Log.log("arm ready");` | `Log.log("arm ready");` |
| `Log.log("bad id", AlertType.kError);` | `Log.log("bad id", Level.HIGH);` | `Log.log("bad id", Log.Severity.ERROR);` |

### 2.12 התראה לפי תנאי (עם פופ-אפ ב-Elastic)

**2026**
```java
Log.alert("Arm motor is hot", motor.getTemperature() > 80);   // בטוח לקרוא בכל לולאה
Log.alert(name + " has an invalid CAN ID");                   // התראה חד-פעמית
```

**2027 לפני** — לא היה. רק `ConsoleAlert` בסיסי, בלי פופ-אפים ובלי זיהוי קצה.

**2027 אחרי** — חזר, אותה התנהגות כמו 2026:
```java
Log.alert("Arm motor is hot", motor.getTemperature() > 80);
Log.alert(name + " has an invalid CAN ID");
```
הפופ-אפ נשלח רק כשהתנאי הופך לנכון. אם Elastic לא מחובר, הפופ-אפ מחכה עד שהוא מחובר שתי שניות. ההודעה היא המזהה של ההתראה, אז היא צריכה להיות קבועה: `Log.alert(isStuck() + "isStuck")` (כמו שהיה בקוד הרובוט של 2026) יוצר התראה חדשה בכל פעם. אחרי 50 הודעות שונות `Log` עוצר ומקפיץ התראה אחת "Too many Log.alert messages".

---

## 3. טבלת המרה מהירה (2027 לפני → 2027 אחרי)

| לפני | אחרי |
|---|---|
| `Log.publishTelemetry(path, this)` + `logTo(TelemetryTable)` | `Log.putData(path, this)` + `log(LogTable)` |
| `implements TelemetryLoggable` / `getTelemetryType()` | `implements Loggable` / `logType()` |
| `Telemetry.log(path, value)` | `Log.put(path, value)` |
| `Log.putData(..., LogLevel.LOG_ONLY, meta, false)` (מקובץ) | למחוק — הערכים כבר נכתבים ב-`log(LogTable)` |
| `LogLevel.LOG_ONLY` על ערך בודד | `Log.Dashboard.DEBUG` |
| `Tunables.publish(path, command)` | `Log.button(path, command)` |
| `Tunables.addBoolean` + `Trigger` + `set(false)` | `Log.button(path, command)` |
| `Tunables.publish(path, new ComplexTunable() {...})` | `Log.tune(path, type, t -> {...})` |
| `Selectable<T>` + `Tunables.publish` | `Log.chooser(path)` |
| `Tunables...publishDouble/publishBoolean(name, get, set)` | `Log.tune(path, get, set)` |
| `publishValue(..., double[].class)` | `Log.tuneArray(path, get, set)` |
| `Tunables.publish(path, field)` | `Log.tune(path, field)` |
| `Log.log(msg, Level.HIGH)` | `Log.log(msg, Log.Severity.ERROR)` |

---

## 4. מלכודות שכדאי להכיר

1. **לא לקרוא ל-`Log` מאתחול סטטי שרץ לפני ה-constructor של `Robot`** (למשל `static Chooser c = Log.chooser(...)`). `RobotBase` רושם את ה-backends שלו אחר כך ודורס את שלנו, ואז שום דבר לא נשמר לקובץ.
2. **`Log.Dashboard.DEBUG` צריך להיקבע ברישום הראשון של הנתיב.** אם כבר נכתב ערך לנתיב בלי DEBUG, הסימון המאוחר לא ישפיע.
3. **כפתור שצריך לעבוד כשהרובוט disabled** צריך `.ignoringDisable(true)` על הפקודה. זו התנהגות של WPILib.
4. **chooser בנתיב שכבר קיים** מוחזר בלי להתפרסם (ותופיע אזהרה). לכל chooser נתיב משלו.
5. **ערכים ש-SysId צריך** (Position, Velocity, Acceleration, Voltage) חייבים `recordEveryLoop`, אחרת הצמדת האינדקסים של SysId תזוז.
6. **הודעת `Log.alert` צריכה להיות קבועה.** טקסט שמשתנה עם ערך יוצר התראה חדשה בכל פעם; לטקסט משתנה משתמשים ב-`Log.log`.
7. **שמות בנתיבים השתנו ממעט מקומות:** ערכי מנוע שהיו ב-`/Log/motors/<שם>/is Connected` ו-`current value` נמצאים עכשיו ב-`/Telemetry/motors/<שם>/Is Connected` ו-`Value`. `ElasticGenerator` כבר מעודכן; layouts ישנים שנשמרו ידנית צריך לייצר מחדש.
