package me.nathan.pocketsonar.sonar;

import android.media.AudioRecord;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import me.nathan.pocketsonar.Main;
import me.nathan.pocketsonar.interpretation.Doppler;
import me.nathan.pocketsonar.interpretation.FFT;

public class MotionDetector extends Thread {

    private boolean stop = false;
    private int windowIndex;
    private final AudioRecord recorder;
    private Thread asyncBufferProcessingThread;
    private BlockingQueue<short[]> bufferQueue = new LinkedBlockingQueue<>();

    public MotionDetector(AudioRecord recorder) {
        this.recorder = recorder;
    }

    @Override
    public void run() {
        recorder.startRecording();
        short[] buffer = new short[Main.BUFFER_SIZE];

        runAsyncBufferProcessingThread();
        while (!stop) {
            recorder.read(buffer, 0, buffer.length);

            int subBufferSize = Main.BUFFER_SIZE / 8; // approx 10ms windows
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

    private void processSubBuffer(short[] nextSubBuffer) {
        double[][] frequencies = FFT.extractFrequenciesMagnitudesAndPhases(nextSubBuffer, Main.SAMPLE_RATE);

        double dominantFrequency = getDominantFrequency(frequencies,
                Main.BASE_FREQUENCY - 50,
                Main.BASE_FREQUENCY +50,
                1000);

        //if(dominantFrequency > 0) {
        //    Log.i("sonar.test", "speed: " +
        //            Doppler.calculateSpeedMPH(dominantFrequency) + "  i: " + windowIndex);
        //}

        /*
        for (int i = 0; i < frequencies[1].length; i++) {
            double magnitude = frequencies[1][i];
            double frequency = frequencies[0][i];

            if (frequency > Main.BASE_FREQUENCY + 500 && magnitude > 500) {
                double currentSpeed = Doppler.calculateSpeedMPH(frequency);

            }
        }
         */
    }

    private double getDominantFrequency(double[][] frequencies,
                                        int frequencyMin, int frequencyMax, int minMagnitude) {
        double dominantFrequency = 0;
        double dominantMagnitude = minMagnitude;
        double dominantPhase = 0;
        for (int i = 0; i < frequencies[1].length; i++) {
            double magnitude = frequencies[1][i];
            double frequency = frequencies[0][i];
            double phase = frequencies[2][i];

            if(frequency >= frequencyMin && frequency <= frequencyMax) {
                if(magnitude > dominantMagnitude) {
                    dominantPhase = phase;
                    dominantMagnitude = magnitude;
                    dominantFrequency = frequency;
                }
            }
        }
        Log.i("sonar.test","freq: " + dominantFrequency + " : " + dominantPhase + "rad");
        return dominantFrequency;
    }

    public void end() {
        this.stop = true;
    }
}