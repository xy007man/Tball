package pers.xiaoyu.billiards.utils;

import android.graphics.Point;

public class BilliardsUtils {

    static {
        System.loadLibrary("billiards");
    }

    public static String getVersion() {
        return "v1.0";
    }

    public static Point[] billiardsGuide(String screenPath, String templatePath) {
        return getGuidePostion(screenPath, templatePath);
    }

    public static native Point[] getGuidePostion(String screenPath, String templatePath);
}
