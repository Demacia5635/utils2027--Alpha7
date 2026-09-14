package first.demacia.utils;

import org.wpilib.tunable.ComplexTunable;
import org.wpilib.tunable.TunableTable;
import org.wpilib.tunable.Tunables;

public class RobotCommon implements ComplexTunable {
    private static boolean isRed = true;
    private static boolean isComp = false;

    static {
        Tunables.publish("RC", new RobotCommon());
    }

    public static void init() {}

    public static boolean getIsRed(){
        return isRed;
    }

    public static void setIsRed(boolean newIsRed){
        isRed = newIsRed;
    }

    public static boolean getIsComp(){
        return isComp;
    }

    public static void setIsComp(boolean newIsComp){
        isComp = newIsComp;
    }

 @Override
public void publishTunable(TunableTable table) {
    // builder.setSmartDashboardType("RobotCommon");// no idea on how to do that with new classes
    table.publishBoolean("is red", RobotCommon::getIsRed, RobotCommon::setIsRed);
    table.publishBoolean("is comp", RobotCommon::getIsComp, RobotCommon::setIsComp);
}
}