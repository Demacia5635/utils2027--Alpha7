// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.demacia.kinematics;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

import static first.demacia.kinematics.KinematicsConstants.*;

/** Add your docs here. */
public class DemaciaKinematics {

    private SwerveModuleVelocity[] swerveStates = new SwerveModuleVelocity[4];
    private Pose2d startRobotPosition;
    private Translation2d[] modulePositionOnTheRobot;
    private SwerveModuleVelocity[] lastStates = new SwerveModuleVelocity[4];

    public DemaciaKinematics(Translation2d... modulePositionOnTheRobot) {
        this.startRobotPosition = Pose2d.kZero;
        this.modulePositionOnTheRobot = modulePositionOnTheRobot;
        for (int i = 0; i < 4; i++) {
            swerveStates[i] = new SwerveModuleVelocity();
            lastStates[i] = new SwerveModuleVelocity();
        }

    }

    // public SwerveModuleState[] udiTest(ChassisVelocities wantedVelocities, ChassisVelocities
    // currentVelocities) {
    // if(Math.abs(wantedVelocities.vx) < 0.05 &&
    // Math.abs(wantedVelocities.vy) < 0.05 &&
    // Math.abs(wantedVelocities.omega)>0.01){
    // SwerveModuleState[] rotationStates = new SwerveModuleState[4];
    // for(int i = 0; i < 4; i++){
    // rotationStates[i] = new SwerveModuleState(wantedVelocities.omega
    // * modulePositionOnTheRobot[i].getNorm(),
    // modulePositionOnTheRobot[i].getAngle().plus(Rotation2d.kCW_90deg));
    // }
    // return rotationStates;
    // }

    // if(KinematicsUtilities.isInRange(currentVelocities, 0.03) &&
    // KinematicsUtilities.isInRange(wantedVelocities, 0.03)){
    // for (SwerveModuleState swerveModuleState : lastStates) {
    // swerveModuleState.VelocityMetersPerSecond = 0;
    // };
    // return lastStates;
    // }
    // swerveStates = toSwerveModuleStates(wantedVelocities);
    // lastStates = swerveStates;
    // return swerveStates;
    // }

    /**
     * transforms from chassis Velocities to swerve module states (Velocities in field
     * relative)
     * 
     * @param fieldRelWantedVelocities in field relative Velocities
     * @param fieldRelCurrentVelocities in field relative Velocities
     * @param currentGyroAngle in Rotation2d
     * 
     */
    public SwerveModuleVelocity[] toSwerveModuleStatesWithLimit(ChassisVelocities fieldRelWantedVelocities,
            ChassisVelocities fieldRelCurrentVelocities, Rotation2d currentGyroAngle) {

        ChassisVelocities limitedWantedVel = limitVelocities(fieldRelWantedVelocities, fieldRelCurrentVelocities);
        limitedWantedVel = limitedWantedVel.toRobotRelative(currentGyroAngle);
        swerveStates = toSwerveModuleStates(limitedWantedVel);
        return swerveStates;
    }

    private ChassisVelocities chassisFromRest(double currentV, double wantedV, ChassisVelocities wantedVelocities) {

        if (wantedV < MIN_VELOCITY) { // target is standing
            return new ChassisVelocities(0, 0, wantedVelocities.omega);
        } else { // target is moving
            // we are moving to the required heading and accelerating, no radial limit
            double ratio = Math.clamp(wantedV, currentV, currentV + MAX_DELTA_V) / wantedV;
            return new ChassisVelocities(wantedVelocities.vx * ratio, wantedVelocities.vy * ratio,
                    wantedVelocities.omega);
        }
    }

    private double optimizeAngleChange(double alpha) {
        return alpha > MIN_REVERSE_ANGLE ? alpha - Math.PI : alpha + Math.PI;

    }

    private ChassisVelocities limitVelocities(ChassisVelocities wantedVelocities, ChassisVelocities currentVelocities) {
        double currentVelocity = Math.hypot(currentVelocities.vx, currentVelocities.vy);
        double wantedVelocity = Math.hypot(wantedVelocities.vx, wantedVelocities.vy);

        if (currentVelocity < MIN_VELOCITY) { // we are standing
            return chassisFromRest(currentVelocity, wantedVelocity, wantedVelocities);
        }

        if (wantedVelocity < MIN_VELOCITY) { // target is stop
            // just deaccelrate to stop
            double ratio = Math.max(currentVelocity - MAX_DELTA_V, wantedVelocity) / currentVelocity;
            return new ChassisVelocities(currentVelocities.vx * ratio, currentVelocities.vy * ratio,
                    wantedVelocities.omega);
        }
        // we are moving and target is moving
        double currentVelocityHeading = Math.atan2(currentVelocities.vy, currentVelocities.vx);
        double targetVelocityHeading = Math.atan2(wantedVelocities.vy, wantedVelocities.vx);
        double velocityHeadingDiff = MathUtil.angleModulus(targetVelocityHeading - currentVelocityHeading);
        double targetVelocity = wantedVelocity;

        if (Math.abs(velocityHeadingDiff) < MAX_FAST_TURN_ANGLE) { // small heading change
            // accelerate to target v
            targetVelocity = Math.clamp(targetVelocity, currentVelocity - MAX_DELTA_V, currentVelocity + MAX_DELTA_V);
        } else if (Math.abs(velocityHeadingDiff) > MIN_REVERSE_ANGLE) { // optimization - deaccdelerate and turn the other way

            targetVelocity = currentVelocity - MAX_DELTA_V;
            velocityHeadingDiff = optimizeAngleChange(velocityHeadingDiff);

        } else {
            targetVelocity = Math.clamp(Math.min(MAX_ROTATION_VELOCITY, targetVelocity), currentVelocity - MAX_DELTA_V, currentVelocity + MAX_DELTA_V);
        }

        if (targetVelocity < MIN_VELOCITY) {
            return new ChassisVelocities(0, 0, wantedVelocities.omega);
        }
        // calculate the maximum heading change using the target velocity and allowed
        // radial acceleration
        double maxAngleChange = (MAX_RADIAL_ACCEL / targetVelocity) * CYCLE_DT;
        // set the target angle
        velocityHeadingDiff = Math.clamp(velocityHeadingDiff, -maxAngleChange, maxAngleChange);
        targetVelocityHeading = currentVelocityHeading + velocityHeadingDiff;

        // return the Velocities - using target velocity and target angle
        return new ChassisVelocities(targetVelocity * Math.cos(targetVelocityHeading), targetVelocity * Math.sin(targetVelocityHeading),
                wantedVelocities.omega);
    }

    public ChassisVelocities toChassisVelocities(SwerveModuleVelocity[] swerveStates, double omegaFromGyro) {
        double sumVx = 0;
        double sumVy = 0;

        for (int i = 0; i < 4; i++) {
            double angleFromCenter = modulePositionOnTheRobot[i].getAngle().getRadians();
            double distanceFromCenter = modulePositionOnTheRobot[i].getNorm();
            double currentAngle = swerveStates[i].angle.getRadians();
            double moduleVx = swerveStates[i].velocity * Math.cos(currentAngle);
            double moduleVy = swerveStates[i].velocity * Math.sin(currentAngle);

            double chassisVx = moduleVx - (omegaFromGyro * distanceFromCenter
                    * Math.sin(currentAngle + (omegaFromGyro * 0.02) + angleFromCenter));
            double chassisVy = moduleVy + (omegaFromGyro * distanceFromCenter
                    * Math.cos(currentAngle + (omegaFromGyro * 0.02) + angleFromCenter));

            sumVx += chassisVx;
            sumVy += chassisVy;
        }
        return new ChassisVelocities(sumVx / 4.0, sumVy / 4.0, omegaFromGyro);
    }

    public SwerveModuleVelocity[] toSwerveModuleStates(ChassisVelocities wantedVelocities) {

        double omega = wantedVelocities.omega;

        for (int i = 0; i < 4; i++) {
            double moduleAngleFromCenter = modulePositionOnTheRobot[i].getAngle().getRadians();
            double moduleCurrentAngle = startRobotPosition.getRotation().getRadians();
            Translation2d velocityVector = new Translation2d(
                    wantedVelocities.vx + omega * modulePositionOnTheRobot[i].getNorm()
                            * Math.sin(moduleCurrentAngle + omega * 0.02 + moduleAngleFromCenter),
                    wantedVelocities.vy - omega * modulePositionOnTheRobot[i].getNorm()
                            * Math.cos(moduleCurrentAngle + omega * 0.02 + moduleAngleFromCenter));
            swerveStates[i] = new SwerveModuleVelocity(velocityVector.getNorm(), new Rotation2d(
                    KinematicsUtilities.getAngleFromVector(velocityVector.getX(), velocityVector.getY())));
        }

        swerveStates = factorModuleVelocities(swerveStates);
        return swerveStates;
    }

    private SwerveModuleVelocity[] factorModuleVelocities(SwerveModuleVelocity[] swerveStates) {
        double maxVelocityCalculated = 0;
        for (int i = 0; i < swerveStates.length; i++) {
            double cur = Math.abs(swerveStates[i].velocity);
            if (cur == 0)
                return swerveStates;
            if (cur > maxVelocityCalculated)
                maxVelocityCalculated = cur;
        }
        double factor = MAX_ALLOWED_MODULE_VELOCITY / maxVelocityCalculated;

        if (factor >= 1)
            return swerveStates;

        for (SwerveModuleVelocity state : swerveStates) {
            state.velocity = state.velocity * factor;
        }
        return swerveStates;

    }

}