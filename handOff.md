# handOff — תשתית הלוג 2027

מסמך העברה למי שממשיך את העבודה על תשתית הלוג: מה המצב, מה עוד חסר, ומה לבדוק. נכתב ב-2026-10-25, ברנץ' `log-infrastructure`.

## 1. מצב נוכחי

- **ברנץ':** `log-infrastructure` (נפתח מ-`testing-telemetry`). לא מוזג ואין PR.
- **מה בנוי:** `Log` הוא נקודת הכניסה היחידה לדשבורד, לקובץ, לכיוונון ולהתראות. אין imports של `org.wpilib.telemetry`/`org.wpilib.tunable` מחוץ לחבילת `log`. 47 טסטים עוברים, ובסימולציה כל הבדיקות האוטומטיות עוברות.
- **מסמכים:**
  - [docs/superpowers/specs/2026-10-23-log-infrastructure-design.md](docs/superpowers/specs/2026-10-23-log-infrastructure-design.md) — ה-spec (ההחלטות ולמה).
  - [docs/superpowers/plans/2026-10-23-log-infrastructure.md](docs/superpowers/plans/2026-10-23-log-infrastructure.md) — תוכנית המימוש.
  - [docs/log-usage-2026-vs-2027.md](docs/log-usage-2026-vs-2027.md) — איך משתמשים, בהשוואה ל-2026.
  - [docs/log-classes-explained.md](docs/log-classes-explained.md) — הסבר מחלקה אחרי מחלקה.
- **כלי בדיקה:** [src/main/java/first/robot/LogDemo.java](src/main/java/first/robot/LogDemo.java) (מופעל משורה אחת ב-`Robot.java`) ו-[tools/check-log.ps1](tools/check-log.ps1). איך מריצים: ראו סעיף 5.
- **דברים פתוחים מחוץ לקוד:**
  - `stash@{0}` מכיל את ה-overloads הישנים של `Log.putData(name, getter, setter)` — הוחלפו ב-`Log.tune`. אפשר למחוק (`git stash drop`) אחרי שמוודאים שאין בו משהו נחוץ.
  - `LogDemo`, השורה ב-`Robot.java` ותיקיית `tools/` — לא מקומטים. להחליט אם לשמור (כלי בדיקה) או להסיר לפני מיזוג.

---

## 2. פערים בכיסוי — נתונים שלא נשמרים בקובץ

ממוין לפי חשיבות. "לא בקובץ" = אחרי משחק אי אפשר לדעת מה היה הערך.

### עדיפות גבוהה

| # | פער | איפה | הצעה |
|---|---|---|---|
| G1 | **המיקום של הרובוט (pose), האודומטריה, מצבי המודולים ומהירויות השאסי לא נרשמים בכלל.** יש רק `chassis/gyro angle` ומגרש (`Field2d`) שמפורסם כ-tunable ולא נרשם. בלי זה אי אפשר לנתח נסיעה, אוטונומי או ראייה אחרי משחק. | [Chassis.java:90-92](src/main/java/first/demacia/utils/chassis/Chassis.java#L90-L92), [SwerveModule.java](src/main/java/first/demacia/utils/chassis/SwerveModule.java), [DemaciaPoseEstimator.java](src/main/java/first/demacia/odometry/DemaciaPoseEstimator.java) | `Chassis implements Loggable`, וב-`log(LogTable)`: `put("pose", getPose())`, `put("module states", states)` (מערך struct — נתמך מאז התיקון ב-`LogTable.put`), `put("speeds", chassisSpeeds)`. AdvantageScope מציג את זה ישירות. |
| G2 | **לחיצות על כפתורים (`Log.button`) לא נרשמות.** לא יודעים מתי לחצו "reset gyro" או "set coast". | [Tuning.java:113-118](src/main/java/first/demacia/utils/log/Tuning.java#L113-L118) | לעטוף את הפקודה: `command.beforeStarting(() -> record(path, "pressed"))` או לרשום ב-`StringLogEntry` תחת `Tunables/<path>`. + טסט. |
| G3 | **ערך התחלתי של tunables לא נרשם**, וגם לא שינוי שהקוד עצמו עושה. אם אף אחד לא נגע ב-`kP`, הקובץ לא יודע עם איזה ערך הרובוט רץ. | [Tuning.java:45-57](src/main/java/first/demacia/utils/log/Tuning.java#L45-L57) | לרשום את `getter` פעם אחת בזמן הפרסום. אופציונלית: לבדוק את ה-getter בכל לולאה ולרשום שינוי (`DoubleLogEntry.update` מדלג על כפילויות). |
| G4 | **אילו פקודות רצו ומתי** — לא נרשם (רק `BaseMechanism` ו-`ObjectPose` כותבים את הפקודה הנוכחית שלהם; `Chassis`, `Vision`, `LedManager` לא). | `CommandScheduler` | ב-`Log.start()`: `CommandScheduler.getInstance().onCommandInitialize/onCommandFinish/onCommandInterrupt` → `Log.log("cmd start: " + name)` או entry ייעודי. זה מכסה את כל הרובוט בשורה אחת. |
| G5 | **נתוני מערכת: מתח סוללה, ניצול CAN, זמן לולאה, brownout** — לא נרשמים. | — | `Log.putData("robot/battery", RobotController::getBatteryVoltage)` וכו' ב-`Log.start()`. לבדוק את ה-API המדויק ב-alpha-7 (`RobotController`, `CANStatus`). |

### עדיפות בינונית

| # | פער | איפה | הצעה |
|---|---|---|---|
| G6 | **שינוי מיקום במגרש מהדשבורד** (`Log.tune(path, Field2d)`) לא נרשם. | [Tuning.java:106-111](src/main/java/first/demacia/utils/log/Tuning.java#L106-L111) | יחד עם G1: לכתוב את ה-poses של המגרש גם ל-`Telemetry` בכל לולאה. |
| G7 | **בחירת ברירת המחדל ב-chooser** לא נרשמת — רק בחירה מהדשבורד. אוטונומי שרץ על ברירת המחדל לא משאיר עקבות. | [Chooser.java](src/main/java/first/demacia/utils/log/Chooser.java) | לרשום את השם ב-`addDefault`. חשוב במיוחד ל-chooser של אוטונומי. |
| G8 | **התראה שהתנקתה** — `Log.alert` רושם רק כשהתנאי נהיה נכון, לא כשהוא נהיה שוב לא נכון. ב-2026 הייתה אפשרות `withLogOnRelease`. | [Alerts.java:101-107](src/main/java/first/demacia/utils/log/Alerts.java#L101-L107) | לרשום `"[ALERT CLEARED] ..."` בקצה היורד. |
| G9 | **ראייה ו-LEDs** — `Vision.java`, `LedManager`, `LedStrip` לא כותבים כלום ללוג. `Tag.java` — הלוג בהערה. | [vision/subsystem/](src/main/java/first/demacia/vision/subsystem/), [leds/](src/main/java/first/demacia/utils/leds/) | לפחות: tags שנראו, latency, pose מהמצלמה, מצב LED. חלק מקוד הראייה עוד לא הועבר מ-2026. |
| G10 | **חותמות זמן של אותות CTRE** — ערכים מקבלים את "עכשיו" ולא את זמן הדגימה (החלטה 8 ב-spec). שגיאה של מילישניות. לא חשוב ל-SysId, יכול לחשוב לסינכרון ראייה. | [LogBackend.java](src/main/java/first/demacia/utils/log/LogBackend.java) | אם יידרש: backend שמקבל timestamp ומעביר ל-`TelemetryEntry` ישירות. |

### עדיפות נמוכה

| # | פער | הצעה |
|---|---|---|
| G11 | **מערכים `short[]`/`byte[]`** לא מנותבים ב-`LogTable.put` ויירשמו כטקסט. | להוסיף שני ענפי `instanceof`. |
| G12 | **יחידות מידה** — WPILib 2027 תומך ב-`setProperty("unit", ...)` ו-`Measure`; לא בשימוש. | לשקול כשמוסיפים struct/units. |
| G13 | **struct לטיפוסים שלנו** (מצב מנגנון וכו') — מחוץ ל-scope של ה-spec. | — |

---

## 3. פערים בתצוגה — נרשם בקובץ אבל לא רואים בדשבורד

| # | פער | הצעה |
|---|---|---|
| D1 | **הודעות `Log.log` ו-Alerts לא מופיעות ב-Elastic.** ב-2027 `Alert` עבר לקוד native ולא מתפרסם כטבלת NT. רואים אותם ב-Sim GUI / ב-Driver Station 2027, ובקובץ. התגלה בבדיקה עם `LogDemo`. | לפרסם את ההודעות האחרונות גם כ-`String[]` ב-`Telemetry/console` כדי שיהיה widget ב-Elastic. |
| D2 | **`Log.remove` לא מוחק את הטופיק מ-NT** — ערכים ישנים (למשל אחרי טעינה חוזרת של replay) נשארים בדשבורד. | לבדוק אם `TelemetryRegistry` / ה-backend מאפשרים unpublish. |
| D3 | **Number Slider ב-Elastic** לא אפשר לשנות את `demo/speed` (כנראה טווח ברירת מחדל ‎-1..1), Text Display עבד. | לא באג שלנו; כדאי להגדיר טווח ב-`ElasticGenerator` לסליידרים שהוא מייצר. |
| D4 | **widgets מתים ב-`ElasticGenerator`:** `quest/*` ו-`chassis/reset odmetry` מצביעים על טופיקים שאף אחד לא מפרסם (אין Quest ב-2027). | למחוק או להחזיר כשה-Quest יועבר. |
| D5 | **שמות לא אחידים:** חיישנים כותבים `is Connected` / `Is Connected` / לא כותבים בכלל; `ElasticGenerator` מחפש `is Connected`. | לאחד שם אחד (`Is Connected`) בכל ה-`log(LogTable)`. |

---

## 4. פערים טכניים ועמידות

| # | פער | איפה | הצעה |
|---|---|---|---|
| T1 | **שימוש ב-`Log` מאתחול סטטי לפני ה-constructor של `Robot`** — `RobotBase` רושם backend משלו ודורס את `LogBackend`, ואז שום דבר לא נכתב לקובץ, **בשקט**. מתועד ב-Javadoc, לא נבדק בקוד. | [Log.java:96-139](src/main/java/first/demacia/utils/log/Log.java#L96-L139) | ב-`periodic()` הראשון לבדוק ש-`TelemetryRegistry.getBackend("/")` הוא שלנו, ואם לא — לרשום מחדש ולהקפיץ `Log.alert`. |
| T2 | **רישום מחדש של backend ל-Tunables** מריץ שוב את `publishTunable` של קבוצות, ו-`TuneTable.claim` דוחה את הילדים ("already published") — הקבוצה נשארת ריקה. | [TuneTable.java](src/main/java/first/demacia/utils/log/TuneTable.java) | לזהות פרסום חוזר של אותה קבוצה ולא לבדוק כפילות. |
| T3 | **`DEBUG` נקבע ביצירת ה-entry** — סימון DEBUG אחרי הכתיבה הראשונה לנתיב לא ישפיע. | [LogBackend.java:61-65](src/main/java/first/demacia/utils/log/LogBackend.java#L61-L65) | מתועד. אפשר לבדוק `debugPaths.contains` בכל כתיבה (עלות: חיפוש ב-Set קטן). |
| T4 | **בטיחות threads:** רק `Log.log` בטוח מ-thread אחר. `putData`/`alert`/`tune` מ-thread של ראייה עלולים להישבר. | [Log.java](src/main/java/first/demacia/utils/log/Log.java) | לתעד ב-Javadoc, או לסנכרן. |
| T5 | **`LogReplay.loadLogs` קורא קובץ שלם בתוך לולאת הרובוט** — לוג גדול יגרום loop overrun ארוך. גם `LogReader` מחזיק state סטטי. | [LogReplay.java:112](src/main/java/first/demacia/utils/log/LogReplay.java#L112), [LogReader.java](src/main/java/first/demacia/utils/log/LogReader.java) | לקרוא ב-thread נפרד ולהחליף את הנתונים בסיום. |
| T6 | **קבצי לוג של Phoenix** — בסימולציה הודפס `Signal Logger Started at "./logs/"`. Phoenix כותב קבצי `.hoot` משלו לצד ה-`.wpilog`. לא בדקנו אם זה קורה גם על הרובוט ובאיזה גודל. | — | להחליט: לכבות (`SignalLogger.enableAutoLogging(false)`) או להשתמש בהם לנתונים מדויקים. לבדוק מקום בדיסק. |
| T7 | **ניהול מקום בדיסק** — לא בדקנו אם `DataLogManager` של 2027 מוחק לוגים ישנים כשהדיסק מתמלא (על SystemCore). | — | לבדוק בקוד של `DataLogManager` alpha-7 ועל הרובוט. |
| T8 | **`warnOnce` מסמן אזהרה כ"דווחה" גם כשהיא הלכה ל-stderr** (בזמן אתחול) — אז היא לא תגיע לקובץ אף פעם. | [Log.java:152-161](src/main/java/first/demacia/utils/log/Log.java#L152-L161) | להוסיף ל-Set רק אחרי כתיבה מוצלחת. |
| T9 | **`isElasticConnected` מקצה מערך חיבורים בכל לולאה.** זול, אבל מיותר. | [Log.java:142-149](src/main/java/first/demacia/utils/log/Log.java#L142-L149) | לבדוק רק כשיש פופ-אפ ממתין, או פעם בשנייה. |
| T10 | **`LogBackend.Entry` אין לו דגל "נסגר"** — entry שהוחזק אחרי `removeEntry` ממשיך להעביר לילדים. | [LogBackend.java:106](src/main/java/first/demacia/utils/log/LogBackend.java#L106) | כמו ב-`MultiTelemetryBackend` של WPILib. |
| T11 | **`keepDuplicates` עובר גם ל-NT** — `recordEveryLoop` מפרסם מחדש 4 טופיקים למנוע ב-NT עם KEEP_DUPLICATES, למרות שרק הקובץ צריך. | [LogBackend.java:128-132](src/main/java/first/demacia/utils/log/LogBackend.java#L128-L132) | להעביר `keepDuplicates` רק ל-file entry. |

---

## 5. בדיקות שעוד לא נעשו

| # | מה | איך |
|---|---|---|
| V1 | **SysId על רובוט אמיתי** — נבדק רק בסימולציה. כולל התיקון של תיקיית הלוג (`DataLogManager.getLogDir()`). | להריץ רובוט, ללחוץ `sysID/sysidCommand`, לוודא שאין "No motor logs found". |
| V2 | **LoadLatestLog (replay) על רובוט.** | ללחוץ `replay/LoadLatestLog`, להזיז `replay/time`, לוודא שערכי `replay/...` משתנים. |
| V3 | **מצב תחרות על רובוט** — `RC/is comp` מסתיר ערכי DEBUG. | לסמן `is comp`, לוודא שערך DEBUG קופא בדשבורד וממשיך בקובץ. |
| V4 | **פופ-אפים כש-Elastic מתחבר מחדש באמצע ריצה.** | לנתק ולחבר את Elastic, להפעיל התראה בזמן הניתוק. |
| V5 | **בדיקת `LogDemo` מלאה מול Elastic** (`hello presses`, `speed alert active`). | ראו להלן. |

**איך מריצים את הבדיקה המקומית:**
1. VS Code → `WPILib: Simulate Robot Code` → Sim GUI. לפתוח Elastic.
2. ב-Elastic להוסיף את `Telemetry → demo` ו-`Tunables → demo`, ולעשות את הפעולות (לשנות `speed` ל-6, ללחוץ `say hello`, לבחור `mode`).
3. **לעצור את הסימולציה**, ורק אז: `.\tools\check-log.ps1`. הרצה בזמן שהסימולציה רצה קוראת קובץ לא גמור ותיתן FAIL שגוי.
4. רק סימולציה אחת בכל רגע — שתיים מתנגשות על פורט NT 5810.

---

## 6. טסטים חסרים

ניתנו כ-minor בביקורות ולא נכתבו:
- **`LogBackend`:** רק `double` נבדק; לא מערכים, מחרוזות, `keepDuplicates`, `setProperty`.
- **`LoggedValues`:** אובייקט `Loggable` עם `DEBUG`, `Loggable` שזורק, התאוששות אחרי רישום מחדש.
- **`Tuning`:** טיפול בחריגות רק ל-number (לא array, ילד בקבוצה, listener של chooser); בדיקת כפילות לא מוודאת שהרישום השני לא כתב לקובץ.
- **`Alerts`:** הבהוב מתחת לשנייה (הפופ-אפ נבלע), איפוס ההמתנה בניתוק.
- **`Messages`:** תפוגה ב-`update()`, מיפוי severity.
- **`LogReplay`:** אין טסט ישיר לענף המערכים.

---

## 7. ניקיון קטן

- [docs/MIGRATION-2027.md](docs/MIGRATION-2027.md) עדיין מלמד `Log.publishTelemetry` ו-`Tunables.publish`, שנמחקו. לעדכן או למחוק.
- שורות שורות בהערה עם API ישן ב-`Camera.java` ו-`Tag.java`.
- Javadoc של `Data` עדיין אומר שהיא "מזהה שינויים"; `Data.setFrequancyAll` ריקה ולא בשימוש.
- שורות ריקות כפולות בחיישנים אחרי מחיקת `addLog()`; `import Data` שאולי לא בשימוש ב-`Cancoder`/`Pigeon`.
- הכתיבה של שמות הפקודה (`.command`, `.default`...) כפולה ב-`BaseMechanism.log` וב-`ObjectPose.log` — אפשר helper משותף.
