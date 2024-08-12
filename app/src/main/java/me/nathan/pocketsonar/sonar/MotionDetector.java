package me.nathan.pocketsonar.sonar;

import android.media.AudioRecord;
import android.util.Log;

import java.text.DecimalFormat;

import me.nathan.pocketsonar.Main;
import me.nathan.pocketsonar.interpretation.Doppler;
import me.nathan.pocketsonar.interpretation.FFT;

public class MotionDetector extends Thread {

    private final double MIN_BALL_FREQUENCY_SHIFT = 1000;
    private final double MIN_HAND_FREQUENCY_SHIFT = 300;
    private final double MIN_HAND_MANGITUDE = 300;

    private final AudioRecord recorder;
    private boolean stop = false;

    public MotionDetector(AudioRecord recorder) {
        this.recorder = recorder;
    }

    @Override
    public void run() {
        recorder.startRecording();
        short[] buffer = new short[2048];

        int currentIndex = 0;

        int indexOfPreviousMovement = 0;
        int numberOfConsecutiveMovements = 0;

        while (!stop) {
            currentIndex++;
            recorder.read(buffer, 0, buffer.length);

            double[][] frequencies = FFT.extractFrequenciesAndMagnitudes(buffer, Main.SAMPLE_RATE);

            boolean likelyNoise = isBackgroundDisturbance(frequencies);

            Movement currentMovement = checkForMovement(frequencies, currentIndex);
            if(currentMovement != null) {
                if(!likelyNoise) {

                    // (near) consecutive movement indicates the player is
                    // throwing the baseball.
                    if(currentMovement.index - indexOfPreviousMovement <= 2) {
                        numberOfConsecutiveMovements++;
                    } else {
                        numberOfConsecutiveMovements = 0;
                    }

                    // after some consecutive movements we can guess the player
                    // is throwing which allows us to use a more sensitive baseball
                    // detection setting with minimal chance of noise interference.
                    if(numberOfConsecutiveMovements >= 4) {
                        if(currentMovement.type.equals(Movement.MovementType.BASEBALL)) {
                            DecimalFormat df1 = new DecimalFormat("0.#");
                            Runnable r = () -> {
                                Main.viewWeakReference.get().setText(
                                        df1.format((Doppler.calculateSpeedMPH(currentMovement.frequency))));
                            };
                            Main.INSTANCE.runOnUiThread(r);
                            Log.i("sonar.result", "" +
                                    Math.round(Doppler.calculateSpeedMPH(currentMovement.frequency)));
                        } else {
                            Log.i("sonar.result", "false");
                        }
                    }

                    indexOfPreviousMovement = currentMovement.index;
                }
            }
        }
        recorder.stop();
        recorder.release();
    }

    private Movement checkForMovement(double[][] frequencies, int iteration) {
        for(int i = frequencies[1].length-1; i >= 0; i--) {

            double magnitude = frequencies[1][i];
            double frequency = frequencies[0][i];

            if(frequency >= Main.BASE_FREQUENCY + MIN_BALL_FREQUENCY_SHIFT) {
                if(magnitude >= Main.MIN_BASEBALL_MAGNITUDE) {
                    return new Movement(frequency, magnitude, iteration, Movement.MovementType.BASEBALL);
                }
            } else if(frequency >= Main.BASE_FREQUENCY + MIN_HAND_FREQUENCY_SHIFT) {
                if(magnitude >= MIN_HAND_MANGITUDE) {
                    return new Movement(frequency, magnitude, iteration, Movement.MovementType.HAND);
                }
            }
        }
        return null;
    }

    private boolean isBackgroundDisturbance(double[][] frequencies) {
        boolean disturbance = false;
        for(int i = 0; i < frequencies[1].length; i++) {
            double magnitude = frequencies[1][i];
            double frequency = frequencies[0][i];
            if(frequency > Main.BASE_FREQUENCY - 2000 && frequency < Main.BASE_FREQUENCY - 1500) {
                if(magnitude > Main.MIN_BASEBALL_MAGNITUDE) {
                    disturbance = true;
                }
            }
        }
        return disturbance;
    }

    public void end() {
        this.stop = true;
    }
}