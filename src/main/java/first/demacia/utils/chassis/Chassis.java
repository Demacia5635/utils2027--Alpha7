// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.demacia.utils.chassis;

import org.wpilib.math.estimator.SwerveDrivePoseEstimator;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Twist2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.system.Timer;
import org.wpilib.smartdashboard.Field2d;
import org.wpilib.smartdashboard.SmartDashboard;
import org.wpilib.command2.InstantCommand;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.framework.RobotBase;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import first.demacia.kinematics.DemaciaKinematics;
import first.demacia.utils.RobotCommon;
import first.demacia.utils.log.Log;
import first.demacia.utils.sensors.Cancoder;
import first.demacia.utils.sensors.Pigeon;
import first.demacia.vision.subsystem.Vision;

public class Chassis extends SubsystemBase {

    private static Chassis instance;

    public static void initialize(ChassisConfig chassisConfig) {
        if (instance == null)
            instance = new Chassis(chassisConfig);
    }

    public static Chassis getInstance() {
        return instance;
    }

    private final ChassisConfig chassisConfig;

    public SwerveModule[] modules;
    public Pigeon gyro;
    
    private DemaciaKinematics demaciaKinematics;
    private SwerveDriveKinematics wpilibKinematics;
    private SwerveDrivePoseEstimator poseEstimator;

    private Field2d field;

    private ChassisVelocities lastVelocitiesFieldRel = new ChassisVelocities();
    private double lastAccelTime = Timer.getTimestamp();

    private double lastOmega = 0;
    private double lastOmegaTime = Timer.getTimestamp();

    private Chassis(ChassisConfig chassisConfig) {
        setName(getName());

        this.chassisConfig = chassisConfig;
        modules = new SwerveModule[4];
        Translation2d[] modulePositions = new Translation2d[4];
        for (int i = 0; i < 4; i++) {
            modules[i] = new SwerveModule(chassisConfig.swerveModuleConfig[i]);
            modulePositions[i] = chassisConfig.swerveModuleConfig[i].position;
        }
        gyro = new Pigeon(chassisConfig.pigeonConfig);

        demaciaKinematics = new DemaciaKinematics(modulePositions);
        wpilibKinematics = new SwerveDriveKinematics(modulePositions);

        poseEstimator = new SwerveDrivePoseEstimator(
                wpilibKinematics,
                getGyroAngle(),
                getModulePositions(),
                new Pose2d());

        field = new Field2d();

        Vision.getInstance();

        addLog();
    }

    public void addLog() {
        Log.putData("chassis/gyro angle", () -> getGyroAngle().getDegrees());

        SmartDashboard.putData("chassis/reset gyro",
                new InstantCommand(() -> setYaw(Rotation2d.kZero)).ignoringDisable(true));
        SmartDashboard.putData("chassis/reset gyro 180",
                new InstantCommand(() -> setYaw(Rotation2d.kPi)).ignoringDisable(true));
        SmartDashboard.putData("chassis/field", field);
        SmartDashboard.putData("chassis/set coast",
                new InstantCommand(() -> setNeutralMode(false)).ignoringDisable(true));
        SmartDashboard.putData("chassis/set brake",
                new InstantCommand(() -> setNeutralMode(true)).ignoringDisable(true));
        SmartDashboard.putData("chassis/reset moduls", new InstantCommand(()-> resetMudolse()).ignoringDisable(true));
    }

    public SwerveDrivePoseEstimator getPoseEstimate() {
        return poseEstimator;
    }

    public void checkElectronics() {
        for (SwerveModule module : modules) {
            module.checkElectronics();
        }
    }

    public void setNeutralMode(boolean isBrake) {
        for (SwerveModule module : modules) {
            module.setNeutralMode(isBrake);
        }
    }

    public ChassisConfig getConfig() {
        return chassisConfig;
    }

    public void restGyro() {
        double gyroAngle = !RobotCommon.getIsRed() ? 0 : 180;
        gyro.setYaw(gyroAngle);
    }

    public void resrtGyro180() {
        double gyroAngle = !RobotCommon.getIsRed() ? 180 : 0;
        gyro.setYaw(gyroAngle);
    }

    public void resetMudolse(){
        for (int i = 0; i < modules.length; i++) {
            modules[i].resetModule();
        }
    }

    public void resetPose(Pose2d pose) {
        poseEstimator.resetPosition(getGyroAngle(), getModulePositions(), pose);
    }

    public Cancoder[] getCancoders() {
        Cancoder[] cancoders = new Cancoder[modules.length];
        for (int i = 0; i < modules.length; i++) {
            cancoders[i] = modules[i].getCancoder();
        }
        return cancoders;
    }

    public Rotation2d getGyroAngle() {
        return gyro.getGyroAngle();
    }

    public double getGyroAngularVelocity() {
        return gyro.getZVelocity();
    }

    public void addVisionMeasurement(Pose2d visionPose, double timestampSeconds) {
        poseEstimator.addVisionMeasurement(visionPose, timestampSeconds);
    }

    public void addVisionMeasurement(Pose2d visionPose, double timestampSeconds,
                                     Matrix<N3, N1> stdDevs) {
        poseEstimator.addVisionMeasurement(visionPose, timestampSeconds, stdDevs);
    }

    public void setYaw(Rotation2d angle) {
        if (angle != null) {
            gyro.setYaw(angle.getDegrees());
            poseEstimator.resetPosition(
                    angle,
                    getModulePositions(),
                    new Pose2d(getPose().getTranslation(), angle));
        }
    }

    public void stop() {
        for (SwerveModule i : modules) {
            i.stop();
        }
    }

    public void setSteerPower(double pow, int id) {
        modules[id].setSteerPower(pow);
    }

    public double getSteerVelocity(int id) {
        return modules[id].getSteerVel();
    }

    public double getSteerAcceleration(int id) {
        return modules[id].getSteerAccel();
    }

    public void setSteerPositions(double position) {
        setSteerPositions(new double[]{position, position, position, position});
    }

    public void setDrivePower(double pow, int id) {
        modules[id].setDrivePower(pow);
    }

    public void setDrivePower(double pow) {
        for (int i = 0; i < 4; i++)
            setDrivePower(pow, i);
    }

    public void setDriveVelocities(double[] velocities) {
        for (int i = 0; i < velocities.length; i++) {
            modules[i].setDriveVelocity(velocities[i]);
        }
    }

    public void setVelocitiesFieldRel(ChassisVelocities velocities) {
        SwerveModuleVelocity[] states = demaciaKinematics.toSwerveModuleStates(velocities);
        setModuleStates(states);

        if (RobotBase.isSimulation()) {
            gyro.getSimState().setRawYaw(Math.toDegrees(gyro.getCurrentYaw() + velocities.omega * 0.02));
        }
    }

    public void setVelocitiesRobotRel(ChassisVelocities velocities) {
        SwerveModuleVelocity[] states = wpilibKinematics.toSwerveModuleVelocities(velocities);
        setModuleStates(states);
    }

    public void setVelocitiesRobotRelWithAccel(ChassisVelocities velocities) {
        ChassisVelocities fieldVelocities = velocities.toFieldRelative(getGyroAngle());
        setVelocitiesFieldRel(fieldVelocities);
    }

    public void setSteerPositions(double[] positions) {
        for (int i = 0; i < positions.length; i++) {
            modules[i].setSteerPosition(positions[i]);
        }
    }

    public void setModuleStates(SwerveModuleVelocity[] states) {
        for (int i = 0; i < states.length; i++) {
            modules[i].setState(states[i]);
        }
    }

    public double getMaxDriveVelocity() {
        return chassisConfig.maxDriveVelocity;
    }

    public double getMaxRotationalVelocity() {
        return chassisConfig.maxRotationalVelocity;
    }

    public Translation2d getVelocityAsVector() {
        return new Translation2d(
                getChassisVelocitiesFieldRel().vx,
                getChassisVelocitiesFieldRel().vy);
    }

    public ChassisVelocities getVelocitiesRobotRel() {
        return getChassisVelocitiesFieldRel().toRobotRelative(getGyroAngle());
    }

    /**
     * Returns linear acceleration [ax, ay] in m/s² (field-relative)
     * and angular acceleration [alpha] in rad/s², derived from velocity delta.
     */
    public double[] getAcceleration() {
        double now = Timer.getTimestamp();
        double dt = now - lastAccelTime;

        ChassisVelocities currentVelocitiesFieldRel = getChassisVelocitiesFieldRel();

        double ax = (currentVelocitiesFieldRel.vx - lastVelocitiesFieldRel.vx) / dt;
        double ay = (currentVelocitiesFieldRel.vy - lastVelocitiesFieldRel.vy) / dt;
        double aOmga = (currentVelocitiesFieldRel.omega - lastVelocitiesFieldRel.omega) / dt;

        lastVelocitiesFieldRel = currentVelocitiesFieldRel;
        lastAccelTime = now;

        return new double[]{ax, ay, aOmga};
    }

    /**
     * Returns angular acceleration (alpha) in rad/s² from the gyro.
     */
    public double getAngularAcceleration() {
        double now = Timer.getTimestamp();
        double dt = now - lastOmegaTime;

        double currentOmega = getGyroAngularVelocity();
        double alpha = (currentOmega - lastOmega) / dt;

        lastOmega = currentOmega;
        lastOmegaTime = now;

        return alpha;
    }

    public ChassisVelocities getChassisVelocitiesRobotRel() {
        return demaciaKinematics.toChassisVelocities(
                getModuleStates(),
                Math.toRadians(gyro.getCurrentYaw()));
    }

    public ChassisVelocities getChassisVelocitiesFieldRel() {
        return demaciaKinematics.toChassisVelocities(getModuleStates(), getGyroAngularVelocity())
        .toFieldRelative(getGyroAngle());
    }

    public Translation2d getChassisVelocitiesVector() {
        ChassisVelocities s = getChassisVelocitiesFieldRel();
        return new Translation2d(s.vx, s.vy);
    }

    public Pose2d getPose() {
        return poseEstimator.getEstimatedPosition();
    }

    public Pose2d getPoseWithVelocity(double dt) {
        Pose2d currentPose = getPose();
        ChassisVelocities currentVelocities = getChassisVelocitiesFieldRel();
        return new Pose2d(
                currentPose.getX() + (currentVelocities.vx * dt),
                currentPose.getY() + (currentVelocities.vy * dt),
                currentPose.getRotation().plus(new Rotation2d(currentVelocities.omega * dt)));
    }

    public Pose2d getFuturePose(double dtSeconds) {
        ChassisVelocities velocities = getChassisVelocitiesFieldRel();

        Twist2d twist = velocities.toTwist2d(dtSeconds);

        return getPose().plus(twist.exp());
    }

    public SwerveModuleVelocity[] getModuleStates() {
        SwerveModuleVelocity[] res = new SwerveModuleVelocity[modules.length];
        for (int i = 0; i < modules.length; i++) {
            res[i] = modules[i].getState();
        }
        return res;
    }

    private SwerveModulePosition[] getModulePositions() {
        SwerveModulePosition[] arr = new SwerveModulePosition[modules.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = modules[i].getModulePosition();
        }
        return arr;
    }

    @Override
    public void periodic() {
        poseEstimator.update(getGyroAngle(), getModulePositions());

        field.setRobotPose(getPose());
    }
}