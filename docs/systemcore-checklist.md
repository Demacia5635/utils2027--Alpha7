# SystemCore — מה צריך ורשימת בדיקות

מסמך הכנה לחיבור הקוד שלנו (`utils2027--Alpha7`, WPILib `2027.0.0-alpha-7`) לבקר SystemCore במקום ה-roboRIO. שלושה חלקים: מה צריך להכין, מה השתנה בבקר ואיך זה פוגע בקוד שלנו, ורשימת בדיקות לפי סדר. נכתב ב-2026-10-30.

מקורות: התיעוד הרשמי של WPILib 2027 (alpha-7) — *New for 2027*, *Known Issues*, ודפי החומרה של Smart I/O. דברים שלא הצלחתי לאמת בתיעוד מסומנים **(לא מאומת)**, וצריך לבדוק אותם על הרובוט.

---

## 1. מה צריך לפני שמתחילים

### חומרה
- [ ] בקר SystemCore + ספק כוח.
- [ ] רדיו VH-109 עם קושחה **2.0.0 ומעלה**. בגרסאות ישנות יותר הרדיו לא נותן ל-SystemCore את הכתובת `10.TE.AM.2`.
- [ ] כבל רשת או USB למחשב ה-Driver Station.
- [ ] כונן USB לקבצי לוג (אופציונלי). לבדוק איזה פורמט SystemCore מקבל — התיעוד עדיין מדבר על FAT32 של ה-roboRIO **(לא מאומת)**.
- [ ] ספק 5V חיצוני לפס הלדים. הלדים לא מקבלים כוח מהבקר.
- [ ] דאטהשיט לכל חיישן שמתחבר ל-Smart I/O, כדי לוודא שהוא עובד ב-3.3V (ראו סעיף 2).

### תוכנה
- [ ] WPILib 2027 alpha-7 מותקן (VS Code + כלים).
- [ ] **FIRST Driver Station** החדש. זה לא ה-NI DS הישן.
- [ ] **Elastic alpha 9 ומעלה**. `Selectable` (ה-chooser החדש) לא עובד עם גרסאות ישנות יותר.
- [ ] Phoenix Tuner X ו-REV Hardware Client בגרסאות שתומכות ב-2027 — לזיהוי התקני CAN.
- [ ] AdvantageScope לקריאת קבצי `.wpilog`.
- [ ] Vendordeps בפרויקט: `Phoenix6-26.70.0-alpha-2`, `REVLib`, `CommandsV2`. לוודא שלכולם יש גרסה ל-2027.
- [ ] Image עדכני על ה-SystemCore (Reimage לפי המדריך *Quick Start for Returning Teams*).

### פרטי גישה
- Hostname: `robot.local`. כתובת ה-IP מוצגת **על המסך של הבקר**.
- SSH ו-SFTP: משתמש `systemcore`, סיסמה `systemcore`. **לא לשנות את הסיסמה** — אחרת deploy יפסיק לעבוד.
- תיקיית קבצי deploy על הבקר: `/home/systemcore/deploy` (מוגדר ב-`build.gradle`).

---

## 2. מה השתנה מול ה-roboRIO, ואיפה זה פוגע בקוד שלנו

| נושא | roboRIO | SystemCore | השפעה על הקוד שלנו |
|---|---|---|---|
| **פורטים** | 10 DIO, 4 אנלוג, 10 PWM — כל סוג בנפרד | **6 פורטי Smart I/O (0–5)**. כל פורט מוגדר בקוד כ-DIO, אנלוג או PWM | ⚠️ **`LedConstants.PORT = 9` — פורט 9 לא קיים.** צריך פורט בין 0 ל-5. כל החיישנים, הלדים וה-encoders חולקים 6 פורטים בלבד — צריך לתכנן חלוקה. |
| **מתח בפורטים** | 5V | **3.3V** (פין כוח ואות) | חיישנים שבנויים ל-5V (חלק מחיישני האור / beam break, MA3) לא בהכרח יעבדו. לבדוק כל חיישן. |
| **כניסה דיגיטלית** | pull-**up**: פורט פתוח קורא `true` | pull-**down**: פורט פתוח קורא `false` | ⚠️ **`LimitSwitch` יתהפך.** החיבור משתנה: מתג מחובר בין אות ל-3.3V (לא לאדמה). צריך לבדוק מחדש את `withInvert` בכל מתג. |
| **כניסה אנלוגית** | 0–5V, יש oversampling/averaging ו-accumulator | **0–3.3V**, 12 ביט, **בלי** averaging ובלי accumulator | `OpticalSensor`, `LidarSensorConfig`, `AnalogEncoder` — טווחי המתח וההמרות מניחים 5V (ה-Javadoc אומר "0–5V"). צפוי יותר רעש כי אין ממוצע. ג'ירו אנלוגי לא יעבוד. |
| **Duty cycle** | החומרה מודדת תדר | החומרה **לא מודדת תדר**. בלי `setAssumedFrequency` יש קריסה. | `DigitalEncoder` כבר קורא `setAssumedFrequency(config.frequency)`. ברירת המחדל היא `1000` — לוודא שזה תואם לדאטהשיט של כל encoder. `getFrequency()` לא ישמש כבדיקת חיבור. |
| **לדים** | תזמון WS2812 ניתן לשינוי | תזמון קבוע, **רק WS2812B ותואמים**. WS2815 ו-DotStar לא נתמכים. | לוודא שהפס שלנו הוא WS2812B. |
| **CAN** | באס native אחד, `"rio"` | כמה באסים native. ב-WPILib יש enum `CANPort` (`CAN_S0`–`CAN_S4`, `CAN_D0`–`CAN_D19`). ב-Phoenix 2027 יש בנאי חדש `new CANBus(CANPort)` לבאסים של SystemCore. | ⚠️ **שתי שיטות כתובת לאותו באס:** TalonFX, Pigeon ו-CANcoder משתמשים ב-`new CANBus("rio")`, ו-Spark משתמש ב-`CANPort.CAN_D0`. לא ברור אם `"rio"` עדיין ממופה לבאס native ב-SystemCore, ולאיזה מחבר פיזי `CAN_D0` מתאים **(לא מאומת)**. |
| **קיבוץ אותות CTRE** | — | — | `Data` מחלק אותות CTRE לשתי קבוצות: `rioSignals` ו-`canivoreSignals`. ב-Phoenix, רענון משותף דורש שכל האותות בקבוצה יהיו על אותו באס. אם התקני "Rio" יפוצלו בין כמה באסים native, הרענון ייכשל. |
| **IMU** | `BuiltInAccelerometer` | **`OnboardIMU`** מובנה (yaw, quaternion, תאוצה). `BuiltInAccelerometer` לא נתמך. | אנחנו משתמשים ב-Pigeon, אז לא נפגענו. אפשר לשקול את `OnboardIMU` כגיבוי. צריך להגדיר `MountOrientation` (`FLAT` / `LANDSCAPE` / `PORTRAIT`). |
| **I2C** | `Onboard` / `MXP` | פורטים ממוספרים | `ColorSensor` משתמש ב-`I2C.Port.PORT_0`. ב-alpha-1 המספור היה הפוך מהמדבקה על הבקר; תוקן ב-alpha-2. לוודא שהחיישן מחובר לפורט שמסומן 0. |
| **Driver Station** | NI DS: Teleop / Auto / Test / Practice | FIRST DS: Teleop / Auto / **Utility** / **Match**, ובחירת OpMode | `Robot.java` נשאר `TimedRobot` (עדיין נתמך), ויש בו `utilityInit`/`utilityPeriodic`. ה-DS כולל מנגנוני בטיחות שיכולים לנטרל את הרובוט כשהחיבור האלחוטי חלש. |
| **רשת** | `10.TE.AM.2` | hostname `robot.local`. ה-IP מוצג על המסך. | ב-DS חייבים להגדיר מספר קבוצה. אם ה-DS לא מתחבר — להקליד את ה-IP מהמסך בשדה מספר הקבוצה. |
| **Deploy** | — | כיבוי באמצע deploy **משחית את מערכת הקבצים**, ואז צריך Reimage | לא לכבות את הרובוט בזמן deploy. |
| **לוגים** | `/home/lvuser/logs` או USB | `DataLogManager` כותב ל-USB אם מחובר, אחרת לדיסק הפנימי. מוחק קבצים ישנים כשנשארים פחות מ-50MB פנויים. | הקוד שלנו כבר משתמש ב-`DataLogManager.getLogDir()` ולא בנתיב קבוע. צריך לבדוק את הנתיב בפועל ואת המחיקה על SystemCore (T7 ב-`handOff.md`). |

---

## 3. רשימת בדיקות

מסודר לפי סדר ביצוע: כל שלב מניח שהשלבים הקודמים עברו. בכל בדיקה: מה עושים, ומה צריך לראות כדי שהיא תעבור.

### שלב 0 — לפני שמחברים חומרה (שינויים בקוד)

| # | מה | עובר אם |
|---|---|---|
| 0.1 | לשנות את `LedConstants.PORT` לפורט Smart I/O בין 0 ל-5 | הקוד מתקמפל, הפורט מתועד |
| 0.2 | טבלת חלוקה של 6 פורטי Smart I/O: איזה חיישן או לד בכל פורט, ובאיזה מצב (DIO / אנלוג / PWM) | אין פורט כפול. כל החיישנים נכנסים. |
| 0.3 | לעבור על כל `LimitSwitchConfig` ולבדוק את `withInvert` לאור ההחלפה ל-pull-down | מתועד מה הערך הצפוי בכל מתג |
| 0.4 | לעבור על דאטהשיט של כל חיישן אנלוגי או דיגיטלי ולוודא שהוא עובד ב-3.3V | רשימה: עובד / צריך level shifter / להחליף |
| 0.5 | לעדכן את `DigitalEncoderConfig.frequency` לכל encoder לפי הדאטהשיט | ערך מפורש בכל config ולא ברירת המחדל |
| 0.6 | Build מלא ו-`./gradlew test` | 0 שגיאות, כל הטסטים עוברים |

### שלב 1 — חיבור, רשת ו-deploy

| # | מה | עובר אם |
|---|---|---|
| 1.1 | להדליק את הבקר ולבדוק את המסך | מוצגים מספר קבוצה ו-IP |
| 1.2 | חיבור DS בכבל | ה-DS מראה Communications ירוק |
| 1.3 | חיבור DS דרך הרדיו (אלחוטי) | אותו דבר. אם לא — להקליד IP ידנית (ראו *Known Issues*) |
| 1.4 | `ssh systemcore@robot.local` | נכנסים עם הסיסמה `systemcore` |
| 1.5 | Deploy (`Shift+F5`) | "Build Successful", הלוג נפתח, הקוד עולה בלי exception |
| 1.6 | קבצי `src/main/deploy` הגיעו | `Generated_Elastic_Layout.json` נמצא ב-`/home/systemcore/deploy` |
| 1.7 | Elastic מתחבר | רואים את `/Telemetry/...` ואת `/Tunables/...` |
| 1.8 | Enable / Disable מה-DS ב-Teleop, Auto ו-Utility | הרובוט עובר בין המצבים. `utilityInit` נקרא במצב Utility. |
| 1.9 | ניתוק DS באמצע Enable | הרובוט עובר ל-Disable תוך שבריר שנייה |

### שלב 2 — CAN

| # | מה | עובר אם |
|---|---|---|
| 2.1 | Phoenix Tuner X — לסרוק את כל הבאסים | כל ה-TalonFX, ה-CANcoders וה-Pigeon מופיעים עם ה-ID מ-`RobotChassisConstants` |
| 2.2 | **Pigeon על `Canbus.Rio` (`"rio"`)** — הבדיקה הכי חשובה בשלב הזה | `chassis/gyro angle` משתנה כשמסובבים את הרובוט. אם לא — לעבור ל-`new CANBus(CANPort...)`. |
| 2.3 | מנועים ו-CANcoders על CANivore (USB לבקר) | ה-CANivore מזוהה, כל 8 המנועים ו-4 ה-CANcoders עונים |
| 2.4 | Spark (אם יש) על `CANPort.CAN_D0` | מופיע ב-REV Hardware Client ועונה מהקוד. לרשום לאיזה מחבר פיזי `CAN_D0` מתאים. |
| 2.5 | התקני CTRE על "Rio" ו-Spark על אותו מחבר פיזי | שניהם עובדים יחד |
| 2.6 | רענון אותות `Data` (`rioSignals` / `canivoreSignals`) | אין אזהרות Phoenix על רענון אותות מבאסים שונים |
| 2.7 | ניצול CAN ומוני שגיאות (`CANBus.getStatus()`) | ניצול סביר (פחות מ-~70%), מוני השגיאות לא עולים |
| 2.8 | לנתק מנוע אחד בזמן ריצה | `checkElectronics` / Alert מדווח עליו, שאר הרובוט ממשיך לעבוד |

### שלב 3 — Smart I/O ו-I2C

| # | מה | עובר אם |
|---|---|---|
| 3.1 | `LimitSwitch`: פתוח ↔ סגור | `sensors/<name>/value` מתחלף נכון. **לבדוק גם עם החוט מנותק** (pull-down → `false`). |
| 3.2 | `DigitalEncoder` (duty cycle): לסובב סיבוב מלא | הערך רציף בטווח, אין קריסה, `isConnected` נכון |
| 3.3 | לנתק את ה-`DigitalEncoder` | `isConnected` → `false`, נרשמת הודעה בלוג |
| 3.4 | `AnalogEncoder`: לסובב סיבוב מלא | מכסה את כל הטווח (0 עד 3.3V ולא נעצר ב-66%) |
| 3.5 | `OpticalSensor` / Lidar: חסום ↔ פתוח | יש הבדל ברור בין המצבים. לרשום את רמת הרעש (אין averaging). |
| 3.6 | `ColorSensor` על I2C פורט 0 | הצבע ו-`proximity` מתעדכנים, `checkElectronics` מדווח "OK" |
| 3.7 | לדים (`LedManager`) בפורט החדש | הפס נדלק בצבעים הנכונים, **גם ב-Disable** |
| 3.8 | `Pneumatics` (אם יש) — ה-`CANPort` הנכון | הקומפרסור נדלק ונכבה לפי מתג הלחץ, הזרם נרשם |

### שלב 4 — שאסי (swerve)

| # | מה | עובר אם |
|---|---|---|
| 4.1 | **כיול offsets של המודולים** — כרגע כל ארבעתם `0 // TODO` ב-`RobotChassisConstants` | כל הגלגלים ישרים קדימה כשהמודול במצב 0 |
| 4.2 | `chassis/reset gyro` ו-`reset gyro 180` | הזווית מתאפסת ל-0 או ל-180 |
| 4.3 | `chassis/set coast` / `set brake` ב-Disable | אפשר לדחוף את הרובוט ב-coast ולא ב-brake |
| 4.4 | נסיעה עם `DriveCommand` (כפתור `DRIVE COMMAND`) | הכיוונים נכונים (קדימה = קדימה, field-relative), אין רעידות בהיגוי |
| 4.5 | אודומטריה: לנסוע מטר קדימה בסרגל | ה-pose ב-`chassis/field` זז בערך מטר |
| 4.6 | `chassis/reset moduls` | המודולים חוזרים למצב ה-offset |

### שלב 5 — לוג (בדיקות V1–V5 מ-`handOff.md` + SystemCore)

| # | מה | עובר אם |
|---|---|---|
| 5.1 | איפה נכתב הקובץ: לבדוק את `DataLogManager.getLogDir()` עם USB ובלי USB | הקובץ נמצא, הנתיב מתועד |
| 5.2 | שם הקובץ: `WPILib_TBD_...` לפני חיבור DS, ואחרי החיבור תאריך | שינוי השם קורה |
| 5.3 | הורדת לוג עם DataLogTool (`systemcore`/`systemcore`) | הקובץ ירד ונפתח ב-AdvantageScope |
| 5.4 | SysId (V1): `sysID/sysidCommand` | אין "No motor logs found", הנתונים נכונים |
| 5.5 | Replay (V2): `replay/LoadLatestLog` והזזה של `replay/time` | ערכי `replay/...` משתנים. לבדוק כמה זמן הלולאה נתקעת בזמן הטעינה (T5). |
| 5.6 | מצב תחרות (V3): לסמן `RC/is comp` | ערך DEBUG קופא בדשבורד וממשיך להיכתב בקובץ |
| 5.7 | Elastic מתנתק ומתחבר מחדש (V4) | פופ-אפים שהופעלו בזמן הניתוק מופיעים אחרי החיבור |
| 5.8 | קבצי `.hoot` של Phoenix (T6) | לדעת אם הם נוצרים, איפה, ובאיזה גודל. להחליט אם לכבות. |
| 5.9 | מחיקת לוגים ישנים (T7) | לבדוק שהבקר מוחק קבצים כשהמקום נגמר ולא מתמלא |
| 5.10 | בדיקה ש-backend הלוג שלנו לא נדרס (T1) | קובץ הלוג מכיל ערכים מ-`Log.putData` ולא רק מ-WPILib |

### שלב 6 — ביצועים ועמידות

| # | מה | עובר אם |
|---|---|---|
| 6.1 | זמן לולאה עם כל ה-subsystems | אין "Loop time overrun" קבוע ב-console |
| 6.2 | ניצול CPU וזיכרון (`top` דרך SSH) | יש מרווח. אין דליפת זיכרון אחרי 10+ דקות. |
| 6.3 | רוחב פס אלחוטי | מתחת ל-7 Mbps עם Elastic ומצלמות |
| 6.4 | נסיעה אלחוטית של 5 דקות | ה-DS לא מנטרל את הרובוט מעצמו (*Known Issues*: "Driver Station randomly disabled") |
| 6.5 | Brownout: האצה חזקה עם סוללה חלשה | מתח הסוללה נרשם, הבקר לא עושה ריסט |
| 6.6 | כיבוי והדלקה של כל הרובוט (לא באמצע deploy!) | הקוד עולה לבד, הכל מתחבר מחדש |
| 6.7 | ריסטארט לקוד מה-DS | עולה תוך זמן סביר, אין שגיאות "already published" ב-Tunables (T2) |

### שלב 7 — ראייה

| # | מה | עובר אם |
|---|---|---|
| 7.1 | Limelight ברשת: הטבלאות ב-NT (לפי `CameraConfig.getTableName()`) | `tags/<cam>/see tag` משתנה מול tag |
| 7.2 | `setTo3d` / `setTo2d` | ה-pipeline מתחלף |
| 7.3 | `addVisionMeasurement` | ה-pose במגרש מתקן את עצמו לכיוון ה-tag |

---

## 4. אחרי הבדיקות

- לעדכן את המסמך הזה עם התוצאות (✅ / ❌ ומה ראינו), במיוחד בסעיפים שמסומנים **(לא מאומת)**: המיפוי של `"rio"`, המחבר הפיזי של `CAN_D0`, הפורמט של כונן ה-USB ותיקיית הלוגים.
- כל באג ב-WPILib או בבקר לדווח ב-[wpilibsuite/SystemcoreTesting](https://github.com/wpilibsuite/SystemcoreTesting/).
- את הממצאים בקוד (פורט הלדים, היפוך המתגים, טווחי המתח) לתקן בברנץ' נפרד מ-`log-infrastructure`.
