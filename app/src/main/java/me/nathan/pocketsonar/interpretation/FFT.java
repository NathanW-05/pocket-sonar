package me.nathan.pocketsonar.interpretation;

import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

public class FFT {

    public static double[][] extractFrequenciesMagnitudesAndPhases(short[] sampleData, int sampleRate) {
        // Determine desired time window for analysis (in seconds)
        // You can set this to a fixed value to maintain consistent frequency resolution
        double desiredTimeWindow = (double) sampleData.length / sampleRate; // Duration of the signal in seconds

        // Calculate FFT size based on desired time window and sample rate
        // Ensure fftSize increases when sampleRate increases
        int fftSize = nextPowerOfTwo((int) (sampleRate * desiredTimeWindow));

        // Ensure fftSize is at least as big as sampleData.length
        if (fftSize < sampleData.length) {
            fftSize = nextPowerOfTwo(sampleData.length);
        }

        double[] a = new double[fftSize];

        // Apply the window function and copy to the FFT input array
        double[] windowedSignal = applyBlackmanHarrisWindow(sampleData, sampleData.length);
        System.arraycopy(windowedSignal, 0, a, 0, sampleData.length);
        // Remaining elements in 'a' are zero (zero-padding)

        // Perform the FFT
        DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);
        fft.realForward(a);

        // Prepare the output arrays
        int numBins = fftSize / 2 + 1;
        double[] frequencies = new double[numBins];
        double[] magnitudes = new double[numBins];
        double[] phases = new double[numBins];

        // Frequency resolution
        double freqResolution = (double) sampleRate / fftSize;

        // DC component (k = 0)
        frequencies[0] = 0;
        magnitudes[0] = Math.abs(a[0]); // Real part of DC component
        phases[0] = 0; // Phase is zero for DC component

        // Nyquist component (k = N/2)
        frequencies[numBins - 1] = sampleRate / 2;
        magnitudes[numBins - 1] = Math.abs(a[1]); // Real part of Nyquist component
        phases[numBins - 1] = 0; // Phase is zero or undefined for Nyquist component

        // For k = 1 to N/2 - 1
        for (int k = 1; k < numBins - 1; k++) {
            int index = 2 * k;
            double re = a[index];
            double im = a[index + 1];

            magnitudes[k] = Math.sqrt(re * re + im * im);
            phases[k] = Math.atan2(im, re);
            frequencies[k] = k * freqResolution;
        }

        // Return frequencies, magnitudes, and phases
        double[][] vals = new double[3][numBins];
        vals[0] = frequencies;
        vals[1] = magnitudes;
        vals[2] = phases;
        return vals;
    }

    public static double[] applyBlackmanHarrisWindow(short[] inputSignal, int windowLength) {
        double[] window = generateBlackmanHarrisWindow(windowLength);

        // Apply window to input signal
        double[] outputSignal = new double[inputSignal.length];
        for (int i = 0; i < inputSignal.length; i++) {
            if (i < windowLength) {
                outputSignal[i] = inputSignal[i] * window[i];
            } else {
                // Zero-padding beyond window length
                outputSignal[i] = 0.0;
            }
        }

        return outputSignal;
    }

    private static double[] generateBlackmanHarrisWindow(int windowLength) {
        double[] window = new double[windowLength];
        for (int i = 0; i < windowLength; i++) {
            double a0 = 0.35875;
            double a1 = 0.48829;
            double a2 = 0.14128;
            double a3 = 0.01168;

            double cos1 = Math.cos(2.0 * Math.PI * i / (windowLength - 1));
            double cos2 = Math.cos(4.0 * Math.PI * i / (windowLength - 1));
            double cos3 = Math.cos(6.0 * Math.PI * i / (windowLength - 1));

            window[i] = a0
                    - a1 * cos1
                    + a2 * cos2
                    - a3 * cos3;
        }
        return window;
    }

    public static int nextPowerOfTwo(int n) {
        if (n <= 0) {
            return 1;
        }
        int v = n - 1;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }
}
