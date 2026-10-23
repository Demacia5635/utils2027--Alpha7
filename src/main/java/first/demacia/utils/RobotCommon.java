package first.demacia.utils;

import first.demacia.utils.log.Log;

public class RobotCommon {
    private static boolean isRed = true;
    private static boolean isComp = false;

    static {
        Log.tune("RC", "RobotCommon", t -> {
            t.flag("is red", RobotCommon::getIsRed, RobotCommon::setIsRed);
            t.flag("is comp", RobotCommon::getIsComp, RobotCommon::setIsComp);
        });
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
}
