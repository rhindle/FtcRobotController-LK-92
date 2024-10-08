package org.firstinspires.ftc.teamcode.RobotParts.LegacyBots;

import com.qualcomm.robotcore.hardware.PIDCoefficients;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.JavaUtil;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.Tools.Functions;
import org.firstinspires.ftc.teamcode.Tools.DataTypes.Position;

public class Navigator3 {

   Telemetry telemetry;
   Robot robot;
   Localizer localizer;
   Drivetrain drivetrain;

   Position robotPosition;

   public double v0, v2, v1, v3;
   double driveSpeed, driveAngle, rotate;
   double maxSpeed = 1;
   public double storedHeading = 0;
   public double deltaHeading = 0;
   boolean useFieldCentricDrive = true;
   boolean useHeadingHold = true;
   boolean useHoldPosition = true;
   boolean usePoleFinder = true;
   boolean useAutoDistanceActivation = true;
   boolean useSnapToAngle = false;
   boolean isTrackingPole = false;
   long headingDelay = System.currentTimeMillis();
   long idleDelay = System.currentTimeMillis();

   boolean onTargetByAccuracy = false;

   public double targetX, targetY, targetRot;
   public int navigate = 0;
   int accurate = 1;  // 0 is loose, 1 is tight, more later?
   int navStep = 0;
   private ElapsedTime navTime = new ElapsedTime(ElapsedTime.Resolution.MILLISECONDS);
   int lastStep = 0;

   public PIDCoefficients PIDmovement = new PIDCoefficients(0.06,0.0012,0.006); //.12 0 .035
   public PIDCoefficients PIDrotate = new PIDCoefficients(0.026,0.01,0.00025);  // .03 0 0
   PIDCoefficients PIDmovement_calculated = new PIDCoefficients(0,0,0);
   PIDCoefficients PIDrotate_calculated = new PIDCoefficients(0,0,0);
   long PIDTimeCurrent;
   long PIDTimeLast;
   double errorDistLast;
   double errorRotLast;
   double navAngleLast;

   Position testPosition = null;

   /* Constructor */
   public Navigator3(Robot robot){//}, Localizer localizer,Drivetrain drivetrain){
      construct(robot);//, localizer, drivetrain);
   }

   void construct(Robot robot){//}, Localizer localizer, Drivetrain drivetrain){
      this.robot = robot;
      this.telemetry = robot.telemetry;
      this.localizer = robot.localizer;
      this.drivetrain = robot.drivetrain;
      this.robotPosition = robot.localizer.robotPosition;
   }

   public void init() {
      v0 = 0.0;
      v1 = 0.0;
      v2 = 0.0;
      v3 = 0.0;
      //storedHeading = localizer.returnGlobalHeading();
      //storedHeading = robotPosition.R;
      //setTargetToCurrentPosition();
      if (!robot.useODO) {
         targetX = 0;
         targetY = 0;
         targetRot = 0;
      } else {
         targetX = localizer.odoFieldStart.X;
         targetY = localizer.odoFieldStart.Y;
         targetRot = localizer.odoFieldStart.R;
      }
      storedHeading = targetRot;
      deltaHeading = targetRot;
   }

   public void loop() {

      if (!robot.useODO) {
         userDrive();
         drivetrain.setDrivePowers(v0, v1, v2, v3);
         return;
      }

      telemetry.addData("PIDmov", "PID = %.4f, %.4f, %.4f", PIDmovement.p, PIDmovement.i, PIDmovement.d);
      telemetry.addData("PIDrot", "PID = %.4f, %.4f, %.4f", PIDrotate.p, PIDrotate.i, PIDrotate.d);

      // State machine-ish.  Needs embetterment.  This will determine motor powers then set them
      if (navigate == 1) {
         autoDrive();
      }
      else if (navigate == 2) {
         scriptedNavigation();
      }
      else {
         userDrive();
         if (idleDelay < System.currentTimeMillis() && useHoldPosition) {
            if (useAutoDistanceActivation) robot.sensors.readDistSensors(true);
            if (usePoleFinder) autoPoleCenter();
            autoDrive();
         }
      }

      drivetrain.setDrivePowers(v0, v1, v2, v3);
   }


   // type, x, y, rot
   NavActions[] autoScript2 =  new NavActions[] {
      new NavActions(Actions.MOVEACCURATE, 0, 0, 0),
      new NavActions(Actions.MOVEACCURATE, 24, -20, -90),
      new NavActions(Actions.PAUSE,500),
      new NavActions(Actions.MOVETRANSITION,24, 0, 90),
      new NavActions(Actions.MOVETRANSITION,48, 0, 90),
      new NavActions(Actions.MOVEACCURATE,48, 24, 90),
      new NavActions(Actions.PAUSE,1000),
      new NavActions(Actions.MOVETRANSITION,48, 4, 90),
      new NavActions(Actions.MOVEACCURATE,24, 0, 0),
      new NavActions(Actions.PAUSE,1000),
      new NavActions(Actions.MOVEACCURATE,0,0,0),
      new NavActions(Actions.ENDROUTINE),
   };

   // Simple state machine for scripted navigation actions
   public void scriptedNavigation() {

      // reset the timer each new step (could be used to time out actions;
      // is used for delays)
      if (navStep != lastStep) {
         navTime.reset();
         lastStep=navStep;
      }

      Actions currentAction = autoScript2[navStep].action;

      telemetry.addData("NavStep", navStep);

      // accurate position
      if (currentAction == Actions.MOVEACCURATE) {
         targetX=autoScript2[navStep].parameter1;
         targetY=autoScript2[navStep].parameter2;
         targetRot=autoScript2[navStep].parameter3;
         accurate=1;
         autoDrive();
      }
      // transitional position
      else if (currentAction == Actions.MOVETRANSITION) {
         targetX=autoScript2[navStep].parameter1;
         targetY=autoScript2[navStep].parameter2;
         targetRot=autoScript2[navStep].parameter3;
         accurate=0;
         autoDrive();
      }
      // delay
      else if (currentAction == Actions.PAUSE) {
         if (navTime.milliseconds()>=autoScript2[navStep].parameter1) navStep++;
      }
      // end the navigation
      else if (currentAction == Actions.ENDROUTINE) {
         navigate=0;
      }
   }

   public void autoPoleCenter () {
      double L = robot.sensors.distL;
      double M = robot.sensors.distM;
      double R = robot.sensors.distR;
      double LM = Math.abs(L-M);
      double RM = Math.abs(R-M);
      double closest = Math.min(L, Math.min(R,M));
      isTrackingPole = false;
      if (L == -1 || M == -1 || R == -1) return;   // sensor not read so don't bother continuing
      if (L < 10 || M < 10 || R < 10) {            // at least one sensor is reading low
         if (L < M && LM > 1) {                    // to the left
            setTargetByDeltaRelative(0, 0.125,0);
            isTrackingPole = true;
         }
         if (R < M && RM > 1) {                    // to the right
            setTargetByDeltaRelative(0, -0.125,0);
            isTrackingPole = true;
         }
         //if (M < L && M < R) {                     // apparently centered
            if (closest < 3) {   //was M
               setTargetByDeltaRelative(-0.1,0,0);
               isTrackingPole = true;
            }
            if (closest > 5) {
               setTargetByDeltaRelative(0.1,0,0);
               isTrackingPole = true;
            }
         //}
      }
      robot.sensors.ledRED.setState(!isTrackingPole);
   }

   // Determine motor speeds when under automatic control
   public void autoDrive () {
      double errorDist, deltaX, deltaY, errorRot, pDist, pRot, navAngle;
//      v0 = 0.0;
//      v2 = 0.0;
//      v1 = 0.0;
//      v3 = 0.0;
//      deltaX = targetX - localizer.xPos;  // error in x
//      deltaY = targetY - localizer.yPos;  // error in y
      deltaX = targetX - robotPosition.X;  // error in x
      deltaY = targetY - robotPosition.Y;  // error in y
      telemetry.addData("DeltaX", JavaUtil.formatNumber(deltaX, 2));
      telemetry.addData("DeltaY", JavaUtil.formatNumber(deltaY, 2));
      errorRot = getError(targetRot);  // error in rotation   //20221222 added deltaheading!?
      errorDist = Math.sqrt(Math.pow(deltaX,2) + Math.pow(deltaY,2));  // distance (error) from xy destination

      //exit criteria if destination has been adequately reached
      onTargetByAccuracy = false;
      if (accurate==0 && errorDist<2) {  // no rotation component here
         onTargetByAccuracy = true;
      }
      if (errorDist<0.5 && Math.abs(errorRot)<0.2) {
         onTargetByAccuracy = true;
      }
      if (accurate==2 && errorDist<1 && Math.abs(errorRot)<1) {
         onTargetByAccuracy = true;
      }
      if (accurate==3 && errorDist<2 && Math.abs(errorRot)<5) {  // ~ like 0 but still gets proportional
         onTargetByAccuracy = true;
      }
      if (onTargetByAccuracy) {
         navStep++;
         return;
      }

      navAngle = Math.toDegrees(Math.atan2(deltaY,deltaX));  // angle to xy destination (vector when combined with distance)
 //     // linear proportional at 12", minimum 0.025
 //     pDist = Math.max(Math.min(errorDist/12,1),0.025);
 //     if (accurate==0) pDist = 1;  // don't bother with proportional when hitting transitional destinations
 //     // linear proportional at 15°, minimum 0.025
//      pRot = Math.max(Math.min(Math.abs(errorRot)/15,1),0.025)*Math.signum(errorRot)*-1;

      // 20230910 - Going to try for some proper PID here
      //  Need to refactor all this later to make it less crappy`

      PIDTimeCurrent = System.currentTimeMillis();

      /*
      i += k_i * (current_error * (current_time - previous_time))

      if i > max_i:
      i = max_i
      elif i < -max_i:
      i = -max_i
      */

      // Still need to reset I if we're going to use it.

      if (Math.abs(navAngle-navAngleLast)>45) PIDmovement_calculated.i = 0;  // test - zero the I if we pass through an inflection

      PIDmovement_calculated.p = PIDmovement.p * errorDist;
      PIDmovement_calculated.i += PIDmovement.i * errorDist * ((PIDTimeCurrent - PIDTimeLast) / 1000.0);
      PIDmovement_calculated.i = Math.max(Math.min(PIDmovement_calculated.i,1),-1);
      PIDmovement_calculated.d = PIDmovement.d * (errorDist - errorDistLast) / ((PIDTimeCurrent - PIDTimeLast) / 1000.0);

      pDist =  PIDmovement_calculated.p +  PIDmovement_calculated.i +  PIDmovement_calculated.d;
      pDist = Math.max(Math.min(pDist,1),0.025);
      if (accurate==0) pDist = 1;  // don't bother with proportional when hitting transitional destinations

      PIDrotate_calculated.p = PIDrotate.p * errorRot;
      PIDrotate_calculated.i += PIDrotate.i * errorRot * ((PIDTimeCurrent - PIDTimeLast) / 1000.0);
      PIDrotate_calculated.i = Math.max(Math.min(PIDrotate_calculated.i,1),-1);
      PIDrotate_calculated.d = PIDrotate.d * (errorDist - errorRotLast) / ((PIDTimeCurrent - PIDTimeLast) / 1000.0);

      pRot =  PIDrotate_calculated.p +  PIDrotate_calculated.i +  PIDrotate_calculated.d;
      pRot = Math.max(Math.min(Math.abs(pRot),1),0.025)*Math.signum(pRot)*-1;
      //pRot = Math.max(Math.min(Math.abs(errorRot)/15,1),0.025)*Math.signum(errorRot)*-1;

      PIDTimeLast = PIDTimeCurrent;
      errorDistLast = errorDist;
      errorRotLast = errorRot;
      navAngleLast = navAngle;

      //special cases:  Ramp up the proportional for pole finding (find a better way to do this)
      if (isTrackingPole) {
         pDist = Math.max(Math.min(errorDist/4,1),0.15);
         pRot = Math.max(Math.min(Math.abs(errorRot)/15,1),0.025)*Math.signum(errorRot)*-1;  //increase this?
      }

      telemetry.addData("NavDistance", JavaUtil.formatNumber(errorDist, 2));
      telemetry.addData("NavAngle", JavaUtil.formatNumber(navAngle, 2));
      telemetry.addData("NavRotation", JavaUtil.formatNumber(errorRot, 2));
      telemetry.addData("pDist", JavaUtil.formatNumber(pDist, 2));
      telemetry.addData("pRot", JavaUtil.formatNumber(pRot, 2));

//      navAngle -= localizer.globalHeading;  // need to account for how the robot is oriented
      navAngle -= robotPosition.R;  // need to account for how the robot is oriented
      double autoSpeed = pDist * 1;  // 1 here is maxspeed; could be turned into a variable
      // the following adds the mecanum X, Y, and rotation motion components for each wheel
      v0 = autoSpeed * (Math.cos(Math.toRadians(navAngle)) - Math.sin(Math.toRadians(navAngle))) + pRot;
      v2 = autoSpeed * (Math.cos(Math.toRadians(navAngle)) + Math.sin(Math.toRadians(navAngle))) + pRot;
      v1 = autoSpeed * (Math.cos(Math.toRadians(navAngle)) + Math.sin(Math.toRadians(navAngle))) - pRot;
      v3 = autoSpeed * (Math.cos(Math.toRadians(navAngle)) - Math.sin(Math.toRadians(navAngle))) - pRot;

      // scale to no higher than 1
      double highValue = JavaUtil.maxOfList(JavaUtil.createListWith(Math.abs(v0), Math.abs(v2), Math.abs(v1), Math.abs(v3), 1));
      v0 /= highValue;
      v2 /= highValue;
      v1 /= highValue;
      v3 /= highValue;
   }

   // Determine motor speeds when under driver control
//   public void userDrive (double driveSpeed, double driveAngle, double rotate) {
   public void userDrive () {
//      if (!(driveSpeed == 0 && driveAngle == 0 && rotate == 0)) {
//         idleDelay = System.currentTimeMillis() + 100;
//      }
      if (idleDelay > System.currentTimeMillis()) {
         setTargetToCurrentPosition();
      }

      driveSpeed = Math.pow(driveSpeed, 1);
      v0 = driveSpeed * (Math.cos(driveAngle / 180 * Math.PI) - Math.sin(driveAngle / 180 * Math.PI)) + rotate;
      v2 = driveSpeed * (Math.cos(driveAngle / 180 * Math.PI) + Math.sin(driveAngle / 180 * Math.PI)) + rotate;
      v1 = driveSpeed * (Math.cos(driveAngle / 180 * Math.PI) + Math.sin(driveAngle / 180 * Math.PI)) - rotate;
      v3 = driveSpeed * (Math.cos(driveAngle / 180 * Math.PI) - Math.sin(driveAngle / 180 * Math.PI)) - rotate;

/*      // scale so average motor speed is not more than maxSpeed
      double averageValue = JavaUtil.averageOfList(JavaUtil.createListWith(Math.abs(v0), Math.abs(v2), Math.abs(v1), Math.abs(v3)));
      averageValue = averageValue / maxSpeed;
      if (averageValue > 1) {
         v0 /= averageValue;
         v2 /= averageValue;
         v1 /= averageValue;
         v3 /= averageValue;
      }*/

      // scale so average motor speed is not more than maxSpeed
      // but only if maxspeed <> 1
      if (maxSpeed != 1) {
         double averageValue = JavaUtil.averageOfList(JavaUtil.createListWith(Math.abs(v0), Math.abs(v2), Math.abs(v1), Math.abs(v3)));
         averageValue = averageValue / maxSpeed;
         if (averageValue > 1) {
            v0 /= averageValue;
            v2 /= averageValue;
            v1 /= averageValue;
            v3 /= averageValue;
         }
      }

      // scale to no higher than 1
      double highValue = JavaUtil.maxOfList(JavaUtil.createListWith(Math.abs(v0), Math.abs(v2), Math.abs(v1), Math.abs(v3), 1));
      v0 /= highValue;
      v2 /= highValue;
      v1 /= highValue;
      v3 /= highValue;
   }

   // Get heading error
   public double getError(double targetAngle) {
      double robotError;
      // calculate error in -179 to +180 range  (
      //robotError = targetAngle - getHeading();
//      robotError = targetAngle - robot.returnImuHeading();
//      robotError = targetAngle - localizer.returnGlobalHeading();
      robotError = targetAngle - robotPosition.R;
//      while (robotError > 180)  robotError -= 360;
//      while (robotError <= -180) robotError += 360;
      return Functions.normalizeAngle(robotError);
   }

   public void setMaxSpeed(double maxSpeed) {
      this.maxSpeed = maxSpeed;
   }

   public void beginScriptedNav() {
      navigate=2;
      navStep=0;
      navTime.reset();
   }

   public void beginAutoDrive() {
      navigate=1;
   }

   public void setAutoDrive(boolean boo) {
      if (boo) navigate = 1; else navigate = 0;
   }

   public void setAccuracy(int A){
      accurate = A;
   }

   public boolean isOnTargetByAccuracy() {
      return onTargetByAccuracy;
   }

   public void setTargetToZeroPosition() {
      targetX=0;
      targetY=0;
      targetRot=0;
      resetPID();
   }

   public void cancelAutoNavigation() {
      navigate=0;
//      storedHeading = localizer.returnGlobalHeading();
      storedHeading = robotPosition.R;
   }

   public void setTargetToCurrentPosition() {
//      targetX = Math.round(localizer.xPos);
//      targetY = Math.round(localizer.yPos);
//      targetRot = Math.round(localizer.returnGlobalHeading());
      targetX = Math.round(robotPosition.X);
      targetY = Math.round(robotPosition.Y);
      targetRot = Math.round(robotPosition.R);
      resetPID();
   }

   public void setDeltaHeading() {
      deltaHeading = storedHeading;
   }

   public void toggleFieldCentricDrive() {
      useFieldCentricDrive = !useFieldCentricDrive;
   }

   public void toggleHeadingHold() {
      useHeadingHold = !useHeadingHold;
//      storedHeading = localizer.returnGlobalHeading();
      storedHeading = robotPosition.R;
   }

   public void setUseHeadingHold(boolean boo) {
      useHeadingHold = boo;
//      storedHeading = localizer.returnGlobalHeading();
      storedHeading = robotPosition.R;
   }

   public void setUseHoldPosition(boolean boo) {
      useHoldPosition = boo;
   }

   public void setUseAutoDistanceActivation(boolean boo) {
      useAutoDistanceActivation = boo;
   }

   public void togglePositionHold() {
      useHoldPosition = !useHoldPosition;
      if (useHoldPosition) setTargetToCurrentPosition();
   }

   public void toggleSnapToAngle() {
      useSnapToAngle = !useSnapToAngle;
   }

   public void setTargetByDelta(double X, double Y, double R) {
      //TODO: revamp this to account for field centric operation accounting for starting orientation
      targetX += X;
      targetY += Y;
      targetRot += R;
      storedHeading = targetRot;  //20230910 fix?
      resetPID();
   }

   public boolean setTargetAbsolute(double X, double Y, double R) {
      // check if within bounds
      //if (R < -180 || R > 180) return false;
      if (X < -63 || X > 63) return false;  //63
      if (Y < -63 || Y > 63) return false;  //63
      targetX = X;
      targetY = Y;
      targetRot = Functions.normalizeAngle(R);
      resetPID();
      return true;
   }

   public void setTargetRotBySnapRelative(double R) {
/*      targetRot = localizer.globalHeading;
      targetRot += deltaHeading%45;  //!!!!  */
      targetRot += R;
      targetRot = Math.round(targetRot/45)*45;
      storedHeading = targetRot;
      resetPID();
   }

   public void setTargetByDeltaRelative(double X, double Y, double R) {
//      double rot = localizer.globalHeading;
      double rot = robotPosition.R;
//      slamraRobotPose.X = sX + (rX*Math.cos(Math.toRadians(sR)) - rY*Math.sin(Math.toRadians(sR)));
//      slamraRobotPose.Y = sY + (rX*Math.sin(Math.toRadians(sR)) + rY*Math.cos(Math.toRadians(sR)));
      targetX = targetX + (X * Math.cos(Math.toRadians(rot)) - Y * Math.sin(Math.toRadians(rot)));
      targetY = targetY + (X * Math.sin(Math.toRadians(rot)) + Y * Math.cos(Math.toRadians(rot)));
//!!!!bug?      targetRot += R;
      targetRot += R;
      resetPID();
   }

   public void setUserDriveSettings(double driveSpeed, double driveAngle, double rotate) {
      if (!(driveSpeed == 0 && rotate == 0)) {
         idleDelay = System.currentTimeMillis() + 500;  //was 250 to match rotate?
         if (useAutoDistanceActivation) robot.sensors.readDistSensors(false);
      }
      this.driveSpeed = driveSpeed;
      this.driveAngle = driveAngle;
      this.rotate = rotate;
      // Modify for field centric Drive
      if (useFieldCentricDrive) {
         this.driveAngle = driveAngle - storedHeading + deltaHeading;
      }
      // Modify for Snap to Angle
      if (useSnapToAngle) {
         snapToAngle();
      }
      // Modify for Hold Angle
      if (useHeadingHold || useSnapToAngle) {
         // Correct the heading if not currently being controlled
         // this should probably be incorporated into autodrive
         if (headingDelay <= System.currentTimeMillis()) {  // shouldn't need to check if == 0
            this.rotate = getError(storedHeading) / -15 * (driveSpeed + 0.2);   // base this on speed?
         }
      }

   }

   void snapToAngle() {
//      storedHeading = Math.round(localizer.returnGlobalHeading()/45)*45;
//      storedHeading += deltaHeading%45;
      storedHeading = Math.round(storedHeading/45)*45;
      targetRot = storedHeading;
      resetPID();
   }

   public void handleRotate(double rotate) {
      // overall plan here is to deal with IMU latency
      if (rotate != 0) {
//         storedHeading = localizer.returnGlobalHeading();
         storedHeading = robotPosition.R;
         headingDelay = System.currentTimeMillis() + 250;  // going to ignore the possibility of overflow
      } else if (headingDelay > System.currentTimeMillis()) {
         // keep re-reading until delay has passed
//         storedHeading = localizer.returnGlobalHeading();
         storedHeading = robotPosition.R;
      }
   }

   public void resetPID() {
      PIDmovement_calculated.i = 0;
      PIDrotate_calculated.i = 0;
   }
}
