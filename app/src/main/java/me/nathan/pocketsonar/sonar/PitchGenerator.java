package me.nathan.pocketsonar.sonar;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import me.nathan.pocketsonar.Main;

public class PitchGenerator {

    public static void generateTone(double freqOfTone) {
        Runnable r = () -> {
            double duration = 1; // seconds, we'll loop this to play forever
            int sampleRate = Main.SAMPLE_RATE; // Use a fixed sample rate

            double dnumSamples = duration * sampleRate;
            dnumSamples = Math.ceil(dnumSamples);
            int numSamples = (int) dnumSamples;
            double[] sample = new double[numSamples];
            byte[] generatedSnd = new byte[2 * numSamples];

            for (int i = 0; i < numSamples; ++i) { // Fill the sample array
                sample[i] = Math.sin(freqOfTone * 2 * Math.PI * i / sampleRate);
            }

            // Convert to 16-bit PCM sound array
            int idx = 0;
            for (double dVal : sample) {
                final short val = (short) (dVal * 32767);
                generatedSnd[idx++] = (byte) (val & 0x00ff);
                generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
            }

            // Initialize AudioTrack outside the loop
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                    AudioTrack.MODE_STREAM);

            audioTrack.play();

            while (true) {
                audioTrack.write(generatedSnd, 0, generatedSnd.length);
            }
        };

        Thread toneThread = new Thread(r);
        toneThread.start();
    }

}
