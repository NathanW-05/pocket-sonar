package me.nathan.pocketsonar.sonar;

import android.media.AudioRecord;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import me.nathan.pocketsonar.Main;
import me.nathan.pocketsonar.interpretation.Doppler;
import me.nathan.pocketsonar.interpretation.FFT;

public class DopplerDetector extends Thread {

    private volatile boolean stop = false;
    private final AudioRecord recorder;
    private int currentWindowIndex = 0;
    private Thread asyncBufferProcessingThread;
    private final BlockingQueue<short[]> bufferQueue = new LinkedBlockingQueue<>();

    public DopplerDetector(AudioRecord recorder) {
        this.recorder = recorder;
    }

    @Override
    public void run() {
        recorder.startRecording();

        short[] buffer = new short[Main.BUFFER_SIZE];

        runAsyncBufferProcessingThread();
        while (!stop) {
            int read = recorder.read(buffer, 0, buffer.length);
            if (read > 0) {
                // Divide buffer into 10 ms chunks with 50% overlap
                int subBufferSize = (int) (Main.SAMPLE_RATE * 0.01); // 10 milliseconds
                int stepSize = subBufferSize / 2; // 50% overlap

                for (int i = 0; i <= buffer.length - subBufferSize; i += stepSize) {
                    short[] subBuffer = Arrays.copyOfRange(buffer, i, i + subBufferSize);
                    bufferQueue.offer(subBuffer);
                }
            }
        }
        recorder.stop();
        recorder.release();
    }

    private void runAsyncBufferProcessingThread() {
        Runnable r = () -> {
            while (!stop || !bufferQueue.isEmpty()) {
                try {
                    short[] nextSubBuffer = bufferQueue.take();
                    currentWindowIndex++;
                    extractFrequencies(nextSubBuffer);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        asyncBufferProcessingThread = new Thread(r);
        asyncBufferProcessingThread.start();
    }

    /* Below is the Doppler shift frequency extraction code */

    private final ArrayList<Lead> leads = new ArrayList<>();

    private void extractFrequencies(short[] nextSubBuffer) {
        double[][] frequencies = FFT.extractFrequenciesMagnitudesAndPhases(nextSubBuffer, Main.SAMPLE_RATE);


        for (int i = frequencies[1].length - 1; i >= 0; i--) {
            double frequency = frequencies[0][i];
            double magnitude = frequencies[1][i];
            double phase = frequencies[2][i];

            if (frequency >= Main.BASE_FREQUENCY + 400) {
                if (magnitude >= Main.sonarMinimumMagnitude) {
                    processNextFrequency(frequency, magnitude, phase);
                }
            }
        }
    }

    private void processNextFrequency(double currentFrequency,
                                      double currentMagnitude,
                                      double currentPhase) {

        ArrayList<Lead> leadsToRemove = new ArrayList<>(); // Avoid concurrent modification
        boolean foundMatchLead = false;

        for (Lead lead : leads) {

            double currentSpeed = Doppler.calculateSpeedMPH(currentFrequency);
            double previousSpeed = Doppler.calculateSpeedMPH(lead.getPreviousFrequency());
            double currentSpeedDifference = Math.abs(Math.ceil(currentSpeed) - Math.floor(previousSpeed));

            if(lead.count < 4) {
                if (currentWindowIndex - lead.getPreviousWindowIndex() == 0) continue;
            }

            if(currentWindowIndex - lead.getPreviousWindowIndex() <= 2){ // within 1 index
                if(currentFrequency < lead.getPreviousFrequency()) { // speed decreases over time
                    if(lead.count > 1) { // have set the first speed/phase difference
                        if(currentSpeedDifference >= lead.getPreviousSpeedDifference()) {

                            lead.update(currentFrequency, currentMagnitude, currentPhase,
                                    currentWindowIndex, currentSpeedDifference, -1);

                            if(lead.count == 4) {
                                double originalSpeed = round(lead.speeds.get(0), 1);
                                double speed1 = round(lead.magnitudes.get(0), 1);
                                double speed2 = round(lead.magnitudes.get(1), 1);
                                double speed3 = round(lead.magnitudes.get(2), 1);
                                double speed4 = round(lead.magnitudes.get(3), 1);
                                //double speed5 = round(lead.speeds.get(4), 1);
                                Log.i("sonar.test",  originalSpeed + speed1 + " " +
                                        speed2 + " " + speed3 + " " + speed4 + " " );
                                leadsToRemove.add(lead);
                            }
                            foundMatchLead = true;
                            continue;
                        }
                    } else {
                        lead.update(currentFrequency, currentMagnitude, currentPhase,
                                currentWindowIndex, currentSpeedDifference, -1);
                        continue;
                    }
                }
            }
            leadsToRemove.add(lead);
        }
        if(!foundMatchLead) {
            leads.add(new Lead(currentFrequency, currentMagnitude, currentPhase, currentWindowIndex));
        }
        leads.removeAll(leadsToRemove);
    }

    public static class Lead {
        public double originalFrequency;
        public double originalMagnitude;
        public double originalPhase;
        public int originalWindowIndex;

        public final ArrayList<Double> frequencies = new ArrayList<>();
        public final ArrayList<Double> magnitudes = new ArrayList<>();
        public final ArrayList<Double> phases = new ArrayList<>();
        public final ArrayList<Integer> windowIndexes = new ArrayList<>();
        public final ArrayList<Double> phaseDifferences = new ArrayList<>();
        public final ArrayList<Double> speeds = new ArrayList<>();
        public final ArrayList<Double> speedDifferences = new ArrayList<>();

        int count = 1;

        public Lead(double frequency, double magnitude, double phase, int windowIndex) {
            this.originalFrequency = frequency;
            this.originalMagnitude = magnitude;
            this.originalPhase = phase;
            this.originalWindowIndex = windowIndex;

            frequencies.add(frequency);
            magnitudes.add(magnitude);
            phases.add(phase);
            windowIndexes.add(windowIndex);
            speeds.add(Doppler.calculateSpeedMPH(frequency));
        }

        public void update(double frequency, double magnitude, double phase, int windowIndex,
                           double speedDifference, double phaseDifference) {
            frequencies.add(frequency);
            magnitudes.add(magnitude);
            phases.add(phase);
            windowIndexes.add(windowIndex);
            speeds.add(Doppler.calculateSpeedMPH(frequency));
            speedDifferences.add(speedDifference);
            phaseDifferences.add(phaseDifference);
            count++;
        }

        public double getPreviousFrequency() {
            return frequencies.get(frequencies.size() - 1);
        }

        public double getPreviousSpeedDifference() {
            return speedDifferences.get(speedDifferences.size() -1);
        }

        public double getPreviousMagnitude() {
            return magnitudes.get(magnitudes.size() - 1);
        }

        public double getPreviousPhase() {
            return phases.get(phases.size() - 1);
        }

        public int getPreviousWindowIndex() {
            return windowIndexes.get(windowIndexes.size() - 1);
        }

        public double getPreviousPhaseDifference() {
            return phaseDifferences.isEmpty() ? 0 : phaseDifferences.get(phaseDifferences.size() - 1);
        }
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    public void end() {
        this.stop = true;
        if (asyncBufferProcessingThread != null) {
            asyncBufferProcessingThread.interrupt();
        }
    }
}
