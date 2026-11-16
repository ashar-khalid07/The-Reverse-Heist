package heist;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;

/**
 * Sound effects and music, all synthesised in code at start-up so the game
 * ships without any audio files. To use recorded sounds instead, load them
 * into the same {@code clips} map and nothing else needs to change.
 *
 * If the computer has no working audio device the game simply runs silent.
 */
final class Audio implements Game.SoundSink {
    private static final float RATE = 22050f;
    private static final int SINE = 0, SQUARE = 1, TRIANGLE = 2;

    private final Map<Game.Sound, Clip> clips = new EnumMap<Game.Sound, Clip>(Game.Sound.class);
    private Clip music;
    private volatile boolean ready;
    private volatile boolean muted;

    /**
     * Every call into the sound system happens on this one background thread,
     * because on some machines starting or stopping a sound can block for a
     * moment and that must never stall the game. If sounds pile up, the oldest
     * waiting one is dropped.
     */
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(8),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "audio");
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    Audio() {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                load();
            }
        });
    }

    private void load() {
        try {
            clips.put(Game.Sound.STEP, clip(footstep()));
            clips.put(Game.Sound.DEPOSIT, clip(notes(new double[] {523.25, 659.25, 783.99, 1046.5}, 0.085, 0.32, TRIANGLE, 0.34)));
            clips.put(Game.Sound.DENY, clip(notes(new double[] {155, 0, 130}, 0.09, 0.12, SQUARE, 0.2)));
            clips.put(Game.Sound.SPOTTED, clip(notes(new double[] {740, 1108.7}, 0.075, 0.16, SQUARE, 0.2)));
            clips.put(Game.Sound.CAMERA, clip(notes(new double[] {1568}, 0.06, 0.07, SINE, 0.2)));
            clips.put(Game.Sound.ALARM, clip(alarm()));
            clips.put(Game.Sound.VICTORY, clip(notes(
                    new double[] {523.25, 659.25, 783.99, 1046.5, 783.99, 1046.5, 1318.5}, 0.13, 0.7, TRIANGLE, 0.32)));
            clips.put(Game.Sound.FAIL, clip(notes(new double[] {392, 329.63, 261.63, 196}, 0.22, 0.55, TRIANGLE, 0.34)));
            clips.put(Game.Sound.CLICK, clip(notes(new double[] {660, 880}, 0.035, 0.06, SQUARE, 0.14)));
            clips.put(Game.Sound.VAULT_TICK, clip(vaultTick()));
            clips.put(Game.Sound.TIME_TICK, clip(notes(new double[] {988}, 0.05, 0.06, SQUARE, 0.16)));
            clips.put(Game.Sound.START, clip(notes(new double[] {220, 329.63, 440}, 0.09, 0.3, TRIANGLE, 0.3)));
            music = clip(musicLoop());
            ready = true;
            if (!muted) music.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Throwable noAudio) {
            ready = false;
        }
    }

    @Override
    public void play(final Game.Sound sound) {
        if (!ready || muted) return;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                Clip c = clips.get(sound);
                if (c == null || muted) return;
                try {
                    c.stop();
                    c.setFramePosition(0);
                    c.start();
                } catch (RuntimeException ignored) {
                    // A sound that fails to play is not worth interrupting the game for.
                }
            }
        });
    }

    @Override
    public void toggleMute() {
        muted = !muted;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                if (!ready || music == null) return;
                try {
                    if (muted) {
                        music.stop();
                        for (Clip c : clips.values()) c.stop();
                    } else {
                        music.loop(Clip.LOOP_CONTINUOUSLY);
                    }
                } catch (RuntimeException ignored) {
                    // See play().
                }
            }
        });
    }

    @Override
    public boolean muted() {
        return muted;
    }

    // ------------------------------------------------------------- synthesis

    private static Clip clip(double[] samples) throws Exception {
        byte[] data = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            double s = Math.tanh(samples[i]);
            int v = (int) Math.round(s * 32000);
            data[i * 2] = (byte) (v & 0xff);
            data[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        Clip c = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, format));
        c.open(format, data, 0, data.length);
        return c;
    }

    private static double wave(int type, double phase) {
        double p = phase - Math.floor(phase);
        switch (type) {
            case SQUARE: return p < 0.5 ? 0.6 : -0.6;
            case TRIANGLE: return 4 * Math.abs(p - 0.5) - 1;
            default: return Math.sin(p * Math.PI * 2);
        }
    }

    /** Adds one plucked note to a buffer. With wrap on, the tail carries over to the start (for loops). */
    private static void addNote(double[] buf, double start, double freq, double length, double volume,
            int type, boolean wrap) {
        int first = (int) (start * RATE), count = (int) (length * RATE);
        for (int i = 0; i < count; i++) {
            int index = first + i;
            if (index >= buf.length) {
                if (!wrap) break;
                index %= buf.length;
            }
            double t = i / RATE;
            double envelope = Math.min(1, t / 0.004) * Math.exp(-t * 4.5 / length);
            buf[index] += wave(type, t * freq) * envelope * volume;
        }
    }

    /** A run of notes played one after another; a frequency of 0 is a rest. */
    static double[] notes(double[] freqs, double spacing, double length, int type, double volume) {
        double[] buf = new double[(int) ((spacing * (freqs.length - 1) + length + 0.02) * RATE)];
        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] > 0) addNote(buf, i * spacing, freqs[i], length, volume, type, false);
        }
        return buf;
    }

    private static void addNoise(double[] buf, double start, double length, double volume, double smooth,
            Random random, boolean wrap) {
        int first = (int) (start * RATE), count = (int) (length * RATE);
        double last = 0;
        for (int i = 0; i < count; i++) {
            int index = first + i;
            if (index >= buf.length) {
                if (!wrap) break;
                index %= buf.length;
            }
            double t = i / RATE;
            last += (random.nextDouble() * 2 - 1 - last) * smooth;
            buf[index] += last * Math.exp(-t * 5 / length) * volume;
        }
    }

    static double[] footstep() {
        double[] buf = new double[(int) (0.07 * RATE)];
        addNoise(buf, 0, 0.06, 0.2, 0.25, new Random(3), false);
        addNote(buf, 0, 95, 0.06, 0.16, SINE, false);
        return buf;
    }

    static double[] vaultTick() {
        double[] buf = new double[(int) (0.12 * RATE)];
        addNoise(buf, 0, 0.03, 0.3, 0.7, new Random(5), false);
        addNote(buf, 0, 210, 0.1, 0.25, SQUARE, false);
        addNote(buf, 0.01, 1400, 0.05, 0.1, SINE, false);
        return buf;
    }

    static double[] alarm() {
        double[] buf = new double[(int) (1.7 * RATE)];
        for (int i = 0; i < 8; i++) {
            addNote(buf, i * 0.2, i % 2 == 0 ? 880 : 659.25, 0.26, 0.3, SQUARE, false);
        }
        addNote(buf, 0, 110, 1.6, 0.25, TRIANGLE, false);
        return buf;
    }

    /** An eight-second suspense loop: pulsing bass, soft pad, ticking hats and a sparse melody. */
    static double[] musicLoop() {
        double beat = 0.5;
        double[] buf = new double[(int) (16 * beat * RATE)];
        double[] roots = {110.0, 110.0, 87.31, 98.0};
        double[][] chords = {
            {220.0, 261.63, 329.63}, {220.0, 261.63, 329.63},
            {174.61, 220.0, 261.63}, {196.0, 246.94, 293.66},
        };
        int[] bassPattern = {1, 1, 0, 1, 2, 1, 0, 1};
        Random random = new Random(11);
        for (int bar = 0; bar < 4; bar++) {
            double barStart = bar * 4 * beat;
            for (int step = 0; step < 8; step++) {
                double t = barStart + step * beat / 2;
                if (bassPattern[step] > 0) {
                    addNote(buf, t, roots[bar] * bassPattern[step], 0.24, 0.13, TRIANGLE, true);
                    addNote(buf, t, roots[bar] * bassPattern[step], 0.2, 0.03, SQUARE, true);
                }
                if (step % 2 == 1) addNoise(buf, t, 0.03, 0.035, 0.9, random, true);
            }
            // Soft pad with a slow swell.
            int padFirst = (int) (barStart * RATE), padCount = (int) (4 * beat * RATE);
            for (double f : chords[bar]) {
                for (int i = 0; i < padCount; i++) {
                    double t = i / RATE;
                    double swell = Math.min(1, t / 0.4) * Math.min(1, (4 * beat - t) / 0.4);
                    buf[(padFirst + i) % buf.length] += Math.sin(t * f * Math.PI * 2) * swell * 0.022;
                }
            }
            // Heartbeat thump on beats one and three.
            addNote(buf, barStart, 58, 0.16, 0.2, SINE, true);
            addNote(buf, barStart + 2 * beat, 58, 0.16, 0.14, SINE, true);
        }
        double[][] melody = {{1.0, 659.25}, {3.25, 523.25}, {4.5, 440.0}, {7.0, 493.88}, {7.5, 587.33}};
        for (double[] m : melody) addNote(buf, m[0], m[1], 0.9, 0.045, SINE, true);
        return buf;
    }
}
