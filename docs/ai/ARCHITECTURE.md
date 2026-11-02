# Architecture (utils2027--Alpha7)

Diagrams are Mermaid. GitHub renders them; in VS Code install the "Markdown Preview Mermaid Support" extension (`bierner.markdown-mermaid`) and open the Markdown preview. Generated on 2026-11-02 from a codebase-memory index of `main` (81 Java files) and checked with `mmdc`. Rerun the index after big changes.

## 1. Package dependencies

An arrow means "imports". Dashed arrows are the cycles to avoid making worse.

```mermaid
flowchart TD
    robot["first.robot<br/>(test robot)"]
    mech["utils.mechanisms<br/>BaseMechanism, StateBaseMechanism"]
    chassis["utils.chassis<br/>Chassis, SwerveModule, DriveCommand"]
    motors["utils.motors<br/>BaseMotor, TalonFX/SparkMax/SparkFlex"]
    sensors["utils.sensors<br/>Pigeon, Cancoder, LimitSwitch..."]
    kin["kinematics<br/>DemaciaKinematics"]
    odo["odometry<br/>(unused)"]
    vision["vision / vision.subsystem<br/>Camera, Vision, Tag"]
    log["utils.log<br/>Log, LogEntry, ConsoleAlert, replay"]
    elastic["utils.elastic<br/>ElasticGenerator"]
    sysid["utils.sysid<br/>on-robot SysId"]
    common["utils<br/>Data, RobotCommon, LookUpTable"]

    robot --> chassis
    robot --> motors
    mech --> motors
    mech --> sensors
    mech --> elastic
    chassis --> motors
    chassis --> sensors
    chassis --> kin
    sensors --> motors
    motors --> log
    motors --> elastic
    motors --> sysid
    sensors --> log
    sensors --> elastic
    vision --> log
    vision --> elastic
    log --> common
    log --> sysid
    sysid --> motors
    common --> log
    chassis -.-> vision
    vision -.-> chassis
    odo -.-> chassis
    elastic -.-> mech
    elastic -.-> chassis
    elastic -.-> vision
```

## 2. One 20 ms loop

```mermaid
sequenceDiagram
    participant R as Robot.robotPeriodic
    participant S as CommandScheduler
    participant L as Log.periodic
    participant D as Data
    participant M as Mechanism / Chassis periodic
    participant C as Commands (DefaultCommand, DriveCommand)
    R->>S: run()
    S->>L: subsystem periodic (Log is a SubsystemBase)
    L->>D: refreshAll() (one bulk CAN refresh)
    L->>L: expire alerts, write log entries
    L->>L: Telemetry.log() every registered object
    S->>M: other subsystems' periodic()
    M->>D: getters read cached values
    S->>C: execute()
    C->>M: setState / setAngle / setVelocities...
```

`Log` is one subsystem among the others, so its place in the order is not guaranteed. A subsystem whose `periodic()` runs before `Log.periodic()` reads values from the previous loop (20 ms old).

## 3. From config to a running mechanism

```mermaid
flowchart LR
    cfg["TalonFXConfig(name, id, Canbus)<br/>.withRadiansMotor().withPID(9 values)"] --> motor["new TalonFXMotor(cfg)"]
    motor -->|constructor| steps["createMotor → configMotor → setSignals → addLog<br/>→ Log.publishTelemetry → Elastic + SysId register"]
    scfg["LimitSwitchConfig(channel, name)"] --> sensor["new LimitSwitch(scfg)"]
    motor --> mech["StateBaseMechanism(name, motors[], sensors[], State.class)"]
    sensor --> mech
    mech --> dc["DefaultCommand(mech, ControlMode[])"]
    dash["Dashboard: State Chooser / Test Values"] --> mech
```

## 4. Dashboard data paths (2027)

```mermaid
flowchart LR
    obj["TelemetryLoggable<br/>(motors, sensors, chassis)"] -->|Log.publishTelemetry| tel["/Telemetry/..."]
    val["Log.putData(name, supplier)"] --> nt["/Log/... + .wpilog file"]
    tun["Tunables / ComplexTunable / Selectable"] --> tunables["/Tunables/..."]
    tel --> elastic["Elastic layout<br/>(ElasticGenerator)"]
    tunables --> elastic
```
