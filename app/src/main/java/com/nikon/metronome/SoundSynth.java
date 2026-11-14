package com.nikon.metronome;

/**
 * Generates the click samples in memory. No audio assets are shipped, so the
 * APK stays small and every sound is reproducible on any Android device.
 */
final class SoundSynth {

    private SoundSynth() {
    }

    static short[] render(MetronomeEngine.SoundType type, boolean accent, int sampleRate) {
        int durationMs;
        switch (type) {
            case PIANO:
                durationMs = 460;
                break;
            case WOODBLOCK:
                durationMs = 170;
                break;
            case ELECTRONIC:
                durationMs = 90;
                break;
            case MECHANICAL:
            default:
                durationMs = 120;
                break;
        }

        int frames = (int) (sampleRate * durationMs / 1000.0);
        short[] pcm = new short[frames];
        double base = accent ? 1.18 : 1.0;

        for (int i = 0; i < frames; i++) {
            double t = i / (double) sampleRate;
            double wave;

            switch (type) {
                case PIANO: {
                    double f = (accent ? 880.0 : 659.25);
                    double attack = 1.0 - Math.exp(-t * 900.0);
                    double decay = Math.exp(-t * 5.0);
                    wave = Math.sin(2 * Math.PI * f * t)
                            + 0.46 * Math.sin(2 * Math.PI * f * 2.003 * t) * Math.exp(-t * 5.8)
                            + 0.23 * Math.sin(2 * Math.PI * f * 3.011 * t) * Math.exp(-t * 7.5)
                            + 0.11 * Math.sin(2 * Math.PI * f * 4.021 * t) * Math.exp(-t * 9.5)
                            + 0.05 * Math.sin(2 * Math.PI * f * 5.032 * t) * Math.exp(-t * 12.0);
                    wave += noise(i) * Math.exp(-t * 130.0) * 0.14;
                    wave *= attack * decay * 0.42 * base;
                    break;
                }
                case WOODBLOCK: {
                    double f = accent ? 800.0 : 620.0;
                    double decay = Math.exp(-t * 30.0);
                    wave = (Math.sin(2 * Math.PI * f * t)
                            + 0.32 * Math.sin(2 * Math.PI * f * 2.42 * t)
                            + noise(i) * 0.18 * Math.exp(-t * 160.0)) * decay * 0.62 * base;
                    break;
                }
                case ELECTRONIC: {
                    double f = accent ? 1320.0 : 990.0;
                    double s = Math.sin(2 * Math.PI * f * t);
                    wave = (s * 0.32 + Math.signum(s) * 0.24)
                            * Math.exp(-t * 75.0) * 0.6 * base;
                    break;
                }
                case MECHANICAL:
                default: {
                    double f = accent ? 1650.0 : 1180.0;
                    double decay = Math.exp(-t * 52.0);
                    wave = (Math.sin(2 * Math.PI * f * t)
                            + 0.38 * Math.sin(2 * Math.PI * f * 2.71 * t)
                            + noise(i) * 0.3 * Math.exp(-t * 200.0)) * decay * 0.55 * base;
                    break;
                }
            }

            if (wave > 1.0) {
                wave = 1.0;
            } else if (wave < -1.0) {
                wave = -1.0;
            }
            pcm[i] = (short) (wave * Short.MAX_VALUE * 0.92);
        }
        normalise(pcm);
        return pcm;
    }

    /**
     * The four timbres have very different raw peaks (the short electronic
     * click is roughly 10 dB below the piano). Scale every sample to the same
     * near-full-scale peak so the volume slider means the same for all of them
     * and the clicks are clearly audible on a phone speaker.
     */
    private static void normalise(short[] pcm) {
        int peak = 0;
        for (short value : pcm) {
            int magnitude = Math.abs(value);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        int target = (int) (Short.MAX_VALUE * 0.94);
        if (peak == 0 || peak >= target) {
            return;
        }
        double gain = Math.min((double) target / peak, 4.0);
        for (int i = 0; i < pcm.length; i++) {
            int amplified = (int) Math.round(pcm[i] * gain);
            pcm[i] = (short) Math.max(-target, Math.min(target, amplified));
        }
    }

    private static double noise(int i) {
        double v = Math.sin(i * 12.9898) * 43758.5453;
        return (v - Math.floor(v)) * 2.0 - 1.0;
    }
}
