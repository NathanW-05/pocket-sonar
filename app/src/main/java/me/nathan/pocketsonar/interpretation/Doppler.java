package me.nathan.pocketsonar.interpretation;

import android.util.Log;

import me.nathan.pocketsonar.Main;

public class Doppler {

    public static final double speedOfMedium = 343; // m/s
    public static double calibrationAngle = 0;

    public static double calculateSpeedMPH(double frequency) {
        double deltaF = Math.abs(frequency - Main.BASE_FREQUENCY); // hz

        //double angle = Math.abs(90 - Math.abs((calibrationAngle - Main.currentOrientation)));

        //Log.i("sonar.angle", String.valueOf(angle));

        return (2.237 * ((deltaF * speedOfMedium) / (2 * Main.BASE_FREQUENCY)));
                /// Math.cos(Math.toRadians(angle));
    }
}
