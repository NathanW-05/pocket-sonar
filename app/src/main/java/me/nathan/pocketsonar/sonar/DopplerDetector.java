package me.nathan.pocketsonar.sonar;

import android.media.AudioRecord;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import me.nathan.pocketsonar.Main;
import me.nathan.pocketsonar.interpretation.Doppler;
import me.nathan.pocketsonar.interpretation.FFT;

public class DopplerDetector extends Thread {

    private boolean stop = false;
    private int windowIndex;
    private final AudioRecord recorder;
    private Thread asyncBufferProcessingThread;
    private BlockingQueue<short[]> bufferQueue = new LinkedBlockingQueue<>();

    int previousSpeed = -1;
    int previousWindowIndex = -1;
    int previousSpeedDifference = -1;
    int previousWindowDifference = -1;

    public DopplerDetector(AudioRecord recorder) {
        this.recorder = recorder;
    }

    @Override
    public void run() {
        recorder.startRecording();
        short[] buffer = new short[Main.BUFFER_SIZE];

        runAsyncBufferProcessingThread();
        while (!stop) {
            recorder.read(buffer, 0, buffer.length);

            int subBufferSize = Main.BUFFER_SIZE / 4; // approx 10ms windows
            for (int i = 0; i < buffer.length; i += subBufferSize) {
                short[] subBuffer = Arrays.copyOfRange(buffer, i, i + subBufferSize);
                bufferQueue.offer(subBuffer);
            }
        }
        recorder.stop();
        recorder.release();
    }

    private void runAsyncBufferProcessingThread() {
        Runnable r = () -> {
            while (!stop) {
                try {
                    short[] nextSubBuffer = bufferQueue.take();  // Blocking until available
                    windowIndex++;
                    processSubBuffer(nextSubBuffer);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        asyncBufferProcessingThread = new Thread(r);
        asyncBufferProcessingThread.start();
    }

    String possibleSpeed = null;
    int count = 0;
    private void processSubBuffer(short[] nextSubBuffer) {
        double[][] frequencies = FFT.extractFrequenciesMagnitudesAndPhases(nextSubBuffer, Main.SAMPLE_RATE);

        //todo: perhaps we dont use the dominant frequency,
        // like it exits in the range but isnt the top dawg
        ArrayList<Double> shiftedFrequencies = findDopplerShiftedFrequencies(
                frequencies,
                Main.BASE_FREQUENCY + 350,
                Main.BASE_FREQUENCY + 4000,
                Main.sonarMinimumMagnitude);

        if(possibleSpeed != null && count >= 2) {
            Log.i("sonar.result", possibleSpeed);
            possibleSpeed = null;
            count = 0;
        }

        if(!shiftedFrequencies.isEmpty()) {
            ArrayList<Integer> alreadyListed = new ArrayList<>();
            for(Double d : shiftedFrequencies) {
                double frequency = d;
                double currentSpeed = Doppler.calculateSpeedMPH(frequency);
                if(previousSpeed != -1) {
                    if(currentSpeed < previousSpeed) {
                        int speedDifference = previousSpeed - (int)currentSpeed;
                        int windowDifference = windowIndex - previousWindowIndex;
                        if(previousWindowDifference == -1) {
                            possibleSpeed = previousSpeed + " : " + previousWindowIndex;
                            count++;
                            previousWindowDifference = windowDifference;
                            previousSpeedDifference = speedDifference;
                        } else {
                            if(speedDifference + 2 >= (previousSpeedDifference / previousWindowDifference)) {
                                count++;
                            } else {
                                count = 0;
                                previousWindowDifference = -1;
                                possibleSpeed = null;
                            }
                            previousSpeedDifference = speedDifference;
                            previousWindowDifference = windowDifference;
                        }
                    } else {
                        count = 0;
                        previousWindowDifference = -1;
                        possibleSpeed = null;
                    }
                }
                previousSpeed = (int)currentSpeed;
                previousWindowIndex = windowIndex;
                if(!alreadyListed.contains((int)currentSpeed)) {
                    Log.i("sonar.test", "s:" + currentSpeed + " i:" + windowIndex);
                    alreadyListed.add((int)currentSpeed);
                }

            }
        }
    }

    private ArrayList<Double> findDopplerShiftedFrequencies(double[][] frequencies,
                                        int frequencyMin, int frequencyMax, int minMagnitude) {
        ArrayList<Double> shiftedFrequencies = new ArrayList<Double>();

        double baseMagnitude = 0;

        for (int i = 0; i < frequencies[1].length; i++) {
            double magnitude = frequencies[1][i];
            double frequency = frequencies[0][i];

            if(frequency > Main.BASE_FREQUENCY - 50 && frequency < Main.BASE_FREQUENCY + 50) {
                if(magnitude > baseMagnitude) {
                    baseMagnitude = magnitude;
                }
            }

            if(frequency >= frequencyMin && frequency <= frequencyMax) {
                if(magnitude > minMagnitude) {
                    shiftedFrequencies.add(frequency);
                }
            }
        }
        return shiftedFrequencies;
    }

    public void end() {
        this.stop = true;
    }
}