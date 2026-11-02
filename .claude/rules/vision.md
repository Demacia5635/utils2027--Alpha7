---
paths:
  - "src/main/java/first/demacia/vision/**"
---

# Vision (`first/demacia/vision`)

Not the 2026 `RobotPose/Vision` design. 2027 has its own, Limelight-only classes:

- `CameraConfig(name, Translation3d robotToCam, pitch, yaw, isCropping, isObjectCamera)`. The NetworkTables table is `getTableName()` (from the name).
- `subsystem/Camera`: one AprilTag camera. Computes the robot position from a tag, distance, confidence (`getPoseEstemationConfidence()`), and has a dashboard button that sets the gyro yaw from vision.
- `subsystem/Tag`: older per-camera tag class that takes `Supplier`s instead of reading `Chassis`.
- `subsystem/Vision`: singleton. Holds the cameras, averages them (`getTagsPoseEstimation()`), and pushes measurements into `Chassis.getInstance().getPoseEstimate()`.
- `ObjectPose` (two classes, one in `vision` and one in `vision/subsystem`): game-piece position from an object camera.
- `VisionConstants`: camera list, std devs, cropping, prediction gains, and the 2026 field tag tables (`O_TO_TAG`, `TAG_ANGLE`, `TAG_HEIGHT`, hub offsets).

## Rules

- Vision corrects x/y; the heading comes from the gyro (`Chassis.getGyroAngle()`).
- Report the capture timestamp, not the arrival time.
- The tag tables are for the 2026 field. For a new game use `AprilTagFieldLayout` instead of adding hand-made tables, and keep game-specific offsets out of the library.
- `Camera` and `Vision` call `Chassis.getInstance()`; `Chassis` calls `Vision.getInstance()`. Don't deepen this cycle.
