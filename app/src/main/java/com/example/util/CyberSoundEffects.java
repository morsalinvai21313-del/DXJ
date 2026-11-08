package com.example.util;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-tech Sci-Fi Robotic Cyberpunk Audio Synthesizer.
 * Generates realtime telemetry sounds, computer terminal chirps, and robotic processing streams
 * directly via PCM 16-bit audio on the MEDIA stream.
 */
public class CyberSoundEffects {

    private static final String TAG = "CyberSoundEffects";
    private static final int SAMPLE_RATE = 22050;
    private static final AtomicBoolean isProcessingAudioActive = new AtomicBoolean(false);
    private static Thread audioThread = null;

    /**
     * Starts continuous robotic cyberpunk compilation sound effects during conversion/animation.
     */
    public static synchronized void startRoboticAnimationSound() {
        stopRoboticAnimationSound();
        isProcessingAudioActive.set(true);

        audioThread = new Thread(() -> {
            AudioTrack track = null;
            try {
                int minBufSize = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );
                int bufferSize = Math.max(minBufSize, 4096);

                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();

                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();

                track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    track.setVolume(1.0f);
                }

                track.play();

                short[] pcmChunk = new short[1024];
                double phase = 0.0;
                long sampleCount = 0;
                int[] carrierFreqs = {620, 940, 1250, 880, 1600, 740, 1420, 1100, 1850, 980};

                while (isProcessingAudioActive.get()) {
                    int patternIndex = (int) ((sampleCount / 1200) % carrierFreqs.length);
                    double baseFreq = carrierFreqs[patternIndex];

                    for (int i = 0; i < pcmChunk.length; i++) {
                        long totalSample = sampleCount + i;

                        // Robotic frequency modulation & cyber glitching
                        double lfo1 = Math.sin(2.0 * Math.PI * 35.0 * totalSample / SAMPLE_RATE);
                        double lfo2 = Math.sin(2.0 * Math.PI * 8.0 * totalSample / SAMPLE_RATE);
                        double currentFreq = baseFreq + (lfo1 * 75.0) + (lfo2 * 45.0);

                        phase += 2.0 * Math.PI * currentFreq / SAMPLE_RATE;
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI;
                        }

                        // Sci-fi data packet staccato envelope
                        int envelopePeriod = 1100;
                        int posInPeriod = (int) (totalSample % envelopePeriod);
                        double envelope = (posInPeriod < 700) ? 0.65 : 0.15;

                        // Square-softened harmonic robot wave
                        double rawWave = Math.sin(phase) + 0.3 * Math.sin(3.0 * phase);
                        pcmChunk[i] = (short) (rawWave * 12000 * envelope);
                    }

                    track.write(pcmChunk, 0, pcmChunk.length);
                    sampleCount += pcmChunk.length;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Audio synthesis interrupted: " + t.getMessage());
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                        track.release();
                    } catch (Throwable ignored) {}
                }
            }
        }, "CyberAudioSynthesizer");
        audioThread.setPriority(Thread.MAX_PRIORITY - 1);
        audioThread.start();
    }

    /**
     * Stops the continuous robotic sound synthesis immediately.
     */
    public static synchronized void stopRoboticAnimationSound() {
        isProcessingAudioActive.set(false);
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
    }

    /**
     * Plays a single triumphant robotic confirmation chime upon successful conversion.
     */
    public static void playSuccessChirp() {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                int durationMs = 380;
                int numSamples = (SAMPLE_RATE * durationMs) / 1000;
                short[] buffer = new short[numSamples];
                double phase1 = 0.0;
                double phase2 = 0.0;

                int half = numSamples / 2;
                for (int i = 0; i < numSamples; i++) {
                    double freq = (i < half) ? 1046.50 : 1567.98; // C6 to G6 futuristic harmonic
                    phase1 += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    phase2 += 2.0 * Math.PI * (freq * 1.5) / SAMPLE_RATE;

                    double decay = 1.0 - ((double) i / numSamples);
                    double sample = (Math.sin(phase1) * 0.7 + Math.sin(phase2) * 0.3) * 14000 * decay;
                    buffer[i] = (short) sample;
                }

                track = createStaticTrack(buffer);
                if (track != null) {
                    track.write(buffer, 0, buffer.length);
                    track.play();
                    Thread.sleep(durationMs + 50);
                }
            } catch (Throwable ignored) {
            } finally {
                releaseTrack(track);
            }
        }).start();
    }

    /**
     * Plays a short futuristic warning alert tone on error.
     */
    public static void playErrorBuzz() {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                int durationMs = 300;
                int numSamples = (SAMPLE_RATE * durationMs) / 1000;
                short[] buffer = new short[numSamples];
                double phase = 0.0;

                for (int i = 0; i < numSamples; i++) {
                    double freq = (i % 800 < 400) ? 220.0 : 180.0; // Low frequency digital warning
                    phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
                    double decay = 1.0 - ((double) i / numSamples);
                    buffer[i] = (short) (Math.signum(Math.sin(phase)) * 10000 * decay);
                }

                track = createStaticTrack(buffer);
                if (track != null) {
                    track.write(buffer, 0, buffer.length);
                    track.play();
                    Thread.sleep(durationMs + 50);
                }
            } catch (Throwable ignored) {
            } finally {
                releaseTrack(track);
            }
        }).start();
    }

    private static AudioTrack createStaticTrack(short[] buffer) {
        try {
            int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(minBuf, buffer.length * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                track.setVolume(1.0f);
            }
            return track;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void releaseTrack(AudioTrack track) {
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Throwable ignored) {}
        }
    }
}
