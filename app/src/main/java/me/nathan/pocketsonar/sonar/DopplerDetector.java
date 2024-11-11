package me.nathan.pocketsonar.sonar;

import android.media.AudioRecord;
import android.util.Log;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import me.nathan.pocketsonar.Main;
import me.nathan.pocketsonar.interpretation.Doppler;
import me.nathan.pocketsonar.interpretation.FFT;

public class DopplerDetector extends Thread {

    private boolean stop = false;
    private final AudioRecord recorder;
    private int currentWindowIndex = 0;
    private Thread asyncBufferProcessingThread;
    private BlockingQueue<short[]> bufferQueue = new LinkedBlockingQueue<>();

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
            int subBufferSize = Main.BUFFER_SIZE / 4; // 10ms windows
            int stepSize = subBufferSize / 2; // 50% overlap
            for (int i = 0; i <= buffer.length - subBufferSize; i += stepSize) {
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

    /* Below is the doppler shift frequency extraction code */

    ArrayList<Lead> leads = new ArrayList<>();

    private void extractFrequencies(short[] nextSubBuffer) {
        double[][] frequencies = FFT.extractFrequenciesMagnitudesAndPhases(nextSubBuffer, Main.SAMPLE_RATE);

        for (int i = frequencies[1].length - 1; i >= 0; i--) {
            double frequency = frequencies[0][i];
            double magnitude = frequencies[1][i];
            double phase = frequencies[2][i];

            if(frequency >= Main.BASE_FREQUENCY + 300) {
                if(magnitude >= Main.sonarMinimumMagnitude) {
                    processNextFrequency(frequency, magnitude, phase);
                }
            }
        }
    }

    private void processNextFrequency(double currentFrequency,
                                      double currentMagnitude,
                                      double currentPhase) {

        ArrayList<Lead> leadsToRemove = new ArrayList<>(); // avoid concurrent modification issue
        ArrayList<Lead> possibleHits = new ArrayList<>();
        for(Lead lead : leads) {
            double currentSpeed = Doppler.calculateSpeedMPH(currentFrequency);
            double previousSpeed = Doppler.calculateSpeedMPH(lead.getPreviousFrequency());
            double currentSpeedDifference = previousSpeed - currentSpeed;
            double currentPhaseDifference = Math.abs(((currentPhase - lead.getPreviousPhase() + Math.PI) %
                    (2 * Math.PI)) - Math.PI);

            //if(currentWindowIndex - lead.getPreviousWindowIndex() == 0) continue;

            if(currentSpeed < previousSpeed && currentWindowIndex - lead.getPreviousWindowIndex()
                    <= 1 && currentPhase > lead.getPreviousPhase()) {
                if(lead.count >= 2) {
                    if (currentSpeedDifference > lead.previousSpeedDifference) { // bigger gap in decreasing speed
                        if (currentPhaseDifference > lead.getPreviousPhaseDifference()) { // bigger gap in phase
                            lead.update(currentFrequency, currentMagnitude, currentPhase, currentWindowIndex);
                            lead.previousSpeedDifference = currentSpeedDifference;
                            lead.phaseDifferences.add(currentPhaseDifference);
                            if(lead.count >= 3) {
                                possibleHits.add(lead);
                                leadsToRemove.add(lead);
                            }
                            continue;
                        }
                    }
                    leadsToRemove.add(lead);
                } else {
                    lead.update(currentFrequency, currentMagnitude, currentPhase, currentWindowIndex);
                    lead.previousSpeedDifference = currentSpeedDifference;
                    lead.phaseDifferences.add(currentPhaseDifference);
                }
            } else {
                leadsToRemove.add(lead);
            }
        }
        leadsToRemove.forEach(lead -> leads.remove(lead));

        for(Lead lead : possibleHits) {
            double originalSpeed = round(Doppler.calculateSpeedMPH(lead.originalFrequency), 1);
            double pd1 = round(lead.speeds.get(0), 1);
            double pd2 = round(lead.speeds.get(1), 1);
            double pd3 = round(lead.speeds.get(2), 1);
            Log.i("sonar.test", "s: " + originalSpeed + "  " + pd1 + " " + pd2 + " " + pd3);
        }
        possibleHits.clear();

        leads.add(new Lead(currentFrequency, currentMagnitude, currentPhase, currentWindowIndex));
    }

    public static class Lead {
        public double originalFrequency;
        public double originalMagnitude;
        public double originalPhase;
        public double originalWindowIndex;

        public ArrayList<Double> frequencies = new ArrayList<>();
        public ArrayList<Double> magnitudes = new ArrayList<>();
        public ArrayList<Double> phases = new ArrayList<>();
        public ArrayList<Integer> windowIndexes = new ArrayList<>();
        public ArrayList<Double> phaseDifferences = new ArrayList<>();
        public ArrayList<Double> speeds = new ArrayList<>();

        public double previousSpeedDifference = -1;

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

        public void update(double frequency, double magnitude, double phase, int windowIndex) {
            frequencies.add(frequency);
            magnitudes.add(magnitude);
            phases.add(phase);
            windowIndexes.add(windowIndex);
            speeds.add(Doppler.calculateSpeedMPH(frequency));
            count++;
        }
        public double getPreviousFrequency() {
            return frequencies.get(frequencies.size() - 1);
        }
        public double getPreviousMagnitude() {
            return magnitudes.get(magnitudes.size() -1 );
        }
        public double getPreviousPhase() {
            return phases.get(phases.size() - 1);
        }
        public int getPreviousWindowIndex() {
            return windowIndexes.get(windowIndexes.size() -1);
        }
        public double getPreviousPhaseDifference() {
            return phaseDifferences.get(phaseDifferences.size() -1);
        }
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    public void end() {
        this.stop = true;
    }
}