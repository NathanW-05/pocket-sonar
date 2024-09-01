package me.nathan.pocketsonar.interpretation;

import org.jtransforms.fft.DoubleFFT_1D;

public class FFT {

    public static double[][] extractFrequenciesMagnitudesAndPhases(short[] sampleData, int sampleRate) {
        // Adjust the FFT size with zero padding
        DoubleFFT_1D fft = new DoubleFFT_1D(sampleData.length + 24 * sampleData.length);
        double[] a = new double[(sampleData.length + 24 * sampleData.length) * 2];

        // Apply the Blackman-Harris window and copy to the FFT input array
        System.arraycopy(applyBlackmanHarrisWindow(sampleData, sampleData.length), 0, a, 0, sampleData.length);

        // Perform the FFT
        fft.realForward(a);

        // Prepare the output array to hold frequencies, magnitudes, and phases
        double[][] vals = new double[3][a.length / 2];

        // Calculate frequency, magnitude, and phase
        for (int i = 0; i < a.length / 2; ++i) {
            double re = a[2 * i];
            double im = a[2 * i + 1];
            double mag = Math.sqrt(re * re + im * im);
            double phase = Math.atan2(im, re);

            // Use the correct sample rate for calculating frequencies
            vals[0][i] = (double) sampleRate * i / (a.length / 2);
            vals[1][i] = mag;
            vals[2][i] = phase; // Store the phase in radians
        }

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
}
