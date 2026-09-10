package first.demacia.vision.subsystem;

import java.util.ArrayList;

import org.wpilib.command2.SubsystemBase;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.system.Timer;

import first.demacia.utils.chassis.Chassis;
import first.demacia.vision.CameraConfig;

import static first.demacia.vision.VisionConstants.*;

public class Vision extends SubsystemBase{
    private static Vision vision;

    private ArrayList<Camera> tags;

    private Vision() {
        this.tags = new ArrayList<>();
        for (CameraConfig cameraConfig : CAMERA_CONFIGS) {
            tags.add(new Camera(cameraConfig));
        }
    }

    public static Vision getInstance() {
        if (vision == null) {
            vision = new Vision();
        }
        return vision;
    }

    public void addTag(Camera tag) {
        tags.add(tag);
    }

    public ArrayList<Camera> getTags() {
        return tags;
    }

    public boolean isSeeTag() {
        for(Camera t : tags){
            if(t.isSeeTag()) return true;
        }
        return false;
    }

    private double getCollectedConfidence() {
        double confidence = 0;
        for (Camera tag : tags) {
            if (tag.getRobotPose2d() != null) {
                confidence += tag.getPoseEstemationConfidence();
            }
        }
        return confidence;
    }

    private double normalizeConfidence(double confidence) {
        return getCollectedConfidence() == 0 ? 0 : confidence * (1d / getCollectedConfidence());
    }
    
    public Rotation2d getRobotAngle(){
        for (Camera tag : tags) {
            if (tag.getRobotPose2d() != null) {
                return Rotation2d.fromDegrees(tag.getAngle());
            }
        }
        return null;
    }

    public void addVisionMeasurement(Translation2d PoseEstimation, Matrix<N3, N1> STD) {
        Chassis.getInstance().getPoseEstimate().setVisionMeasurementStdDevs(STD);
        Chassis.getInstance().getPoseEstimate().addVisionMeasurement(
                new Pose2d(PoseEstimation.getX(), PoseEstimation.getY(), Chassis.getInstance().getGyroAngle()),
                Timer.getTimestamp() - 0.05);
    }

    public Pose2d getTagsPoseEstimation() {
        double x = 0;
        double y = 0;
        double confidence = 0;
        for (Camera tag : tags) {
            Pose2d pose2d = tag.getRobotPose2d();
            if (pose2d == null)
                continue;
            confidence = normalizeConfidence(tag.getPoseEstemationConfidence());
            x += pose2d.getX() * confidence;
            y += pose2d.getY() * confidence;
        }
        return new Pose2d(x, y, Chassis.getInstance().getGyroAngle());
    }

    @Override
    public void periodic() {
        if (isSeeTag()) {
            addVisionMeasurement(getTagsPoseEstimation().getTranslation(), LIMELIGHT_STD);
        }
    }
}
