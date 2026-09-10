// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package first.demacia.kinematics;

import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;

/** Add your docs here. */
public class KinematicsUtilities {

    public static double getAngleFromVector(double x, double y){
        return Math.atan2(y, x);
    }
    public static double getNorm(double x, double y){
        return Math.sqrt(x*x + y*y);
    }
    public static Translation2d limitVector(Translation2d vector, Translation2d limit){
        return limitVector(vector, limit.getNorm());
    }
    public static Translation2d limitVector(Translation2d vector, double limit){
        double vectorNorm = vector.getNorm();
        if(vectorNorm > limit){
            return (vector.div(vectorNorm)).times(limit);
        }
        return vector;
    }
    public static boolean isInRange(double value, double limit){
        return Math.abs(value) <= limit;
    }
    public static boolean isInRange(ChassisVelocities velocities, double limit){
        return Math.abs(velocities.vx) <= limit && Math.abs(velocities.vy) <= limit && Math.abs(velocities.omega) <= limit;
    }
}