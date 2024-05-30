package org.firstinspires.ftc.teamcode.robot.DiscShooter;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.robotcore.external.hardware.camera.BuiltinCameraDirection;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.robot.Common.Parts;
import org.firstinspires.ftc.teamcode.robot.Common.TelemetryHandler;
import org.firstinspires.ftc.teamcode.robot.Common.Tools.Position;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagGameDatabase;
import org.firstinspires.ftc.vision.apriltag.AprilTagLibrary;
import org.firstinspires.ftc.vision.apriltag.AprilTagMetadata;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;

import java.util.List;

public class AprilTag {

//    LinearOpMode opMode;
    Parts parts;

    private static final boolean USE_WEBCAM = false;  // true for webcam, false for phone camera
    private AprilTagProcessor aprilTag;
    private VisionPortal visionPortal;
    public Position robotTagPosition;

    /* Constructor */
    public AprilTag(Parts parts){
        construct(parts);
    }

    void construct(Parts parts){
        this.parts = parts;
    }

    public void init() {
        lkAprilTag();
    }

    public void initLoop() {
        updateAprilTag();
    };

    public void loop() {
        updateAprilTag();
    };

    public void preInit() {};
    public void preStart() {};

    public void stop() {
        visionPortal.close();
    };

    public void enableStreaming (boolean streamBoo) {
        if (streamBoo) {
            visionPortal.resumeStreaming();
        } else {
            visionPortal.stopStreaming();
        }
    }

    private void lkAprilTag() {
        AprilTagMetadata myAprilTagMetadata, myAprilTagMetadata2;
        AprilTagLibrary.Builder myAprilTagLibraryBuilder;
        AprilTagProcessor.Builder myAprilTagProcessorBuilder;
        AprilTagLibrary myAprilTagLibrary;

        myAprilTagLibraryBuilder = new AprilTagLibrary.Builder();
        myAprilTagLibraryBuilder.addTags(AprilTagGameDatabase.getCenterStageTagLibrary());

        myAprilTagMetadata = new AprilTagMetadata(20, "LAK 36h11 ID20", 5, DistanceUnit.INCH);

        myAprilTagLibraryBuilder.addTag(myAprilTagMetadata);

        myAprilTagLibrary = myAprilTagLibraryBuilder.build();

        myAprilTagProcessorBuilder = new AprilTagProcessor.Builder();

        myAprilTagProcessorBuilder.setTagLibrary(myAprilTagLibrary);

        aprilTag = myAprilTagProcessorBuilder.build();

        // Adjust Image Decimation to trade-off detection-range for detection-rate.
        // eg: Some typical detection data using a Logitech C920 WebCam
        // Decimation = 1 ..  Detect 2" Tag from 10 feet away at 10 Frames per second
        // Decimation = 2 ..  Detect 2" Tag from 6  feet away at 22 Frames per second
        // Decimation = 3 ..  Detect 2" Tag from 4  feet away at 30 Frames Per Second (default)
        // Decimation = 3 ..  Detect 5" Tag from 10 feet away at 30 Frames Per Second (default)
        // Note: Decimation can be changed on-the-fly to adapt during a match.
        //aprilTag.setDecimation(3);

        // Create the vision portal by using a builder.
        VisionPortal.Builder builder = new VisionPortal.Builder();

        // Set the camera (webcam vs. built-in RC phone camera).
        if (USE_WEBCAM) {
            builder.setCamera(parts.opMode.hardwareMap.get(WebcamName.class, "Webcam 1"));
        } else {
            builder.setCamera(BuiltinCameraDirection.BACK);
        }

        // Choose a camera resolution. Not all cameras support all resolutions.
        //builder.setCameraResolution(new Size(640, 480));

        // Enable the RC preview (LiveView).  Set "false" to omit camera monitoring.
        //builder.enableLiveView(true);

        // Set the stream format; MJPEG uses less bandwidth than default YUY2.
        //builder.setStreamFormat(VisionPortal.StreamFormat.YUY2);

        // Choose whether or not LiveView stops if no processors are enabled.
        // If set "true", monitor shows solid orange screen if no processors enabled.
        // If set "false", monitor shows camera view without annotations.
        //builder.setAutoStopLiveView(false);

        // Set and enable the processor.
        builder.addProcessor(aprilTag);

        // Build the Vision Portal, using the above settings.
        visionPortal = builder.build();

        // Disable or re-enable the aprilTag processor at any time.
        //visionPortal.setProcessorEnabled(aprilTag, true);
    }

    private void updateAprilTag() {

        robotTagPosition = null;
        List<AprilTagDetection> currentDetections = aprilTag.getDetections();
        TelemetryHandler.Message(5,"# AprilTags Detected", currentDetections.size());

        // Step through the list of detections and display info for each one.
        for (AprilTagDetection detection : currentDetections) {
            if (detection.metadata != null) {
                TelemetryHandler.Message(5,String.format("\n==== (ID %d) %s", detection.id, detection.metadata.name));
                TelemetryHandler.Message(5,String.format("XYZ %6.1f %6.1f %6.1f  (inch)", detection.ftcPose.x, detection.ftcPose.y, detection.ftcPose.z));
                TelemetryHandler.Message(5,String.format("PRY %6.1f %6.1f %6.1f  (deg)", detection.ftcPose.pitch, detection.ftcPose.roll, detection.ftcPose.yaw));
                TelemetryHandler.Message(7,String.format("RBE %6.1f %6.1f %6.1f  (inch, deg, deg)", detection.ftcPose.range, detection.ftcPose.bearing, detection.ftcPose.elevation));

                //Vector3 camOffset = new Vector3(-3.25,4,0);
                //Position camOffset = new Position(-6,-2,0);
                Position camOffset = new Position(-1,3,0);

                // raw camera values (ftcPose in it's native coordinate system) of XY
                Position camRaw = new Position(detection.ftcPose.x, detection.ftcPose.y, 0);
                TelemetryHandler.Message(7,String.format("camRaw   XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", camRaw.X, camRaw.Y, camRaw.R));

                // transform the camera raw position using the yaw to align with field
                Position camTrans = transPos(new Position(0, 0, -detection.ftcPose.yaw), camRaw);
                TelemetryHandler.Message(7,String.format("camTrans XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", camTrans.X, camTrans.Y, camTrans.R));

                // rotate the camera XY 90deg to match the field by switching axes
                Position camRot = new Position(-camTrans.Y, camTrans.X, camTrans.R);
                TelemetryHandler.Message(7,String.format("camRot   XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", camRot.X, camRot.Y, camRot.R));

                // Do everything in one step: Switch ftcPose to field XY and transform by yaw to align with field
                Position camTry2 = transPos(new Position(0,0, -detection.ftcPose.yaw),
                        new Position(-detection.ftcPose.y, detection.ftcPose.x, 0 ));
                TelemetryHandler.Message(5,String.format("camTry2  XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", camTry2.X, camTry2.Y, camTry2.R));

                // Switch ftcPose to field XY relative to tag, add robot offset, and transform by yaw to align with field
                Position camPos = transPos(new Position(0,0, -detection.ftcPose.yaw),
                        new Position(-detection.ftcPose.y + camOffset.X, detection.ftcPose.x + camOffset.Y, 0 ));
                TelemetryHandler.Message(5,String.format("camPos   XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", camPos.X, camPos.Y, camPos.R));

                // Get the tag's field position
                // the library has bad x values!  60.3, but in reality it's 63.5.  So let's add 3.2 inches.
                double adjustment = 0; //3.2;
                float[] fieldPos = detection.metadata.fieldPosition.getData();
                TelemetryHandler.Message(7,String.format("field XYR %6.1f %6.1f %6.1f  (inch)", fieldPos[0], fieldPos[1], fieldPos[2]));
                Position tagPos = new Position(detection.metadata.fieldPosition.get(0)+adjustment, detection.metadata.fieldPosition.get(1),0);
                TelemetryHandler.Message(7,String.format("tagPos   XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", tagPos.X, tagPos.Y, tagPos.R));

                // Calculate the robot position based on camera position and tag position
                Position robotPos = new Position(tagPos.X+camPos.X, tagPos.Y+camPos.Y, camPos.R);
                TelemetryHandler.Message(5,String.format("robotPos XYR %6.1f %6.1f %6.1f  (inch, inch, deg)", robotPos.X, robotPos.Y, robotPos.R));
                robotTagPosition = robotPos;

            } else {
                TelemetryHandler.Message(5,String.format("\n==== (ID %d) Unknown", detection.id));
                TelemetryHandler.Message(5,String.format("Center %6.0f %6.0f   (pixels)", detection.center.x, detection.center.y));
            }
        }   // end for() loop

        if (currentDetections.size() == 0) {
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(5,"X");
            TelemetryHandler.Message(7,"X");
            TelemetryHandler.Message(7,"X");
            TelemetryHandler.Message(7,"X");
            TelemetryHandler.Message(7,"X");
            TelemetryHandler.Message(7,"X");
            TelemetryHandler.Message(7,"X");
        }

        // Add "key" information to telemetry
        TelemetryHandler.Message(9,"\nkey:\nXYZ = X (Right), Y (Forward), Z (Up) dist.");
        TelemetryHandler.Message(9,"PRY = Pitch, Roll & Yaw (XYZ Rotation)");
        TelemetryHandler.Message(9,"RBE = Range, Bearing & Elevation");

    }   // end method telemetryAprilTag()

//    void lkUpdateOdoRobotPose() {
//        //pos1 = odoRawPose, pos2 = odoRobotOffset
//        lkOdoRobotPose = lkTransformPosition(lkOdoRawPose, lkOdoRobotOffset);
//    }

    public Position getRobotTagPosition() {
        return robotTagPosition;
    }

    Position transPos(Position pos1, Position pos2) {
        return new Position(
                (pos1.X + (pos2.X*Math.cos(Math.toRadians(pos1.R)) - pos2.Y*Math.sin(Math.toRadians(pos1.R)))),
                (pos1.Y + (pos2.X*Math.sin(Math.toRadians(pos1.R)) + pos2.Y*Math.cos(Math.toRadians(pos1.R)))),
                (pos1.R + pos2.R)
        );
    }
}
