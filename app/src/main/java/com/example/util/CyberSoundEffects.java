package com.example.util;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

public class CyberSoundEffects {

    /**
     * Plays a synthesized sci-fi robotic hacker audio pulse in the background.
     * Uses Android AudioTrack to generate realtime telemetry chirps and frequencies.
     */
    public static void playRoboticHackingSound() {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                int sampleRate = 22050;
                int durationMs = 1350; // ~1.35 seconds sci-fi hacker data chirps
                int numSamples = (sampleRate * durationMs) / 1000;
                short[] buffer = new short[numSamples];

                double phase = 0;
                int[] frequencies = {580, 940, 720, 1350, 820, 1500, 1020, 1720, 1140, 1400};
                int hopInterval = Math.max(1, numSamples / frequencies.length);

                for (int i = 0; i < numSamples; i++) {
                    int freqIndex = Math.min(i / hopInterval, frequencies.length - 1);
                    double freq = frequencies[freqIndex];

                    // Subtle frequency jitter for robotic computer terminal feel
                    double modulation = Math.sin(2.0 * Math.PI * 40.0 * i / sampleRate);
                    double currentFreq = freq + (modulation * 50.0);

                    phase += 2.0 * Math.PI * currentFreq / sampleRate;

                    // Pulsing amplitude envelope
                    int subPulse = i % (hopInterval / 2);
                    double env = (subPulse < (hopInterval / 4)) ? 0.75 : 0.35;
                    buffer[i] = (short) (Math.sin(phase) * 11000 * env);
                }

                int minBufSize = AudioTrack.getMinBufferSize(sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);

                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(Math.max(minBufSize, buffer.length * 2))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(buffer, 0, buffer.length);
                track.play();
                Thread.sleep(durationMs + 100);
            } catch (Exception ignored) {
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                        track.release();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }
}
