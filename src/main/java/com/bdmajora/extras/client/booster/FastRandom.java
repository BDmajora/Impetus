package com.bdmajora.extras.client.booster;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

// Random source with a fast generator.
public final class FastRandom extends Random {
    // Version of the serial form.
    private static final long serialVersionUID = 1L;
    // Whether the source replaces the standard one.
    public static boolean enabled;

    // Increment of the seed mixer.
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    // Source of the seeds.
    private static final AtomicLong SEEDS = new AtomicLong(System.nanoTime());
    // Shared source of the particles.
    private static final FastRandom PARTICLES = new FastRandom();

    // Number of layers of the Gaussian table.
    private static final int LAYERS = 128;
    // Start of the Gaussian tail.
    private static final double TAIL = 3.442619855899;
    // Area of one layer.
    private static final double LAYER_AREA = 9.91256303526217e-3;
    // Scale of the integer samples.
    private static final double SCALE = 2147483648.0;
    // Acceptance limit of each layer.
    private static final double[] THRESHOLD = new double[LAYERS];
    // Width of each layer.
    private static final double[] WIDTH = new double[LAYERS];
    // Height of each layer.
    private static final double[] HEIGHT = new double[LAYERS];

    static {
        double x = TAIL;
        double previous = TAIL;
        double base = LAYER_AREA / Math.exp(-0.5 * TAIL * TAIL);
        THRESHOLD[0] = (x / base) * SCALE;
        THRESHOLD[1] = 0.0;
        WIDTH[0] = base / SCALE;
        WIDTH[LAYERS - 1] = x / SCALE;
        HEIGHT[0] = 1.0;
        HEIGHT[LAYERS - 1] = Math.exp(-0.5 * x * x);
        for (int i = LAYERS - 2; i >= 1; i--) {
            x = Math.sqrt(-2.0 * Math.log(LAYER_AREA / x + Math.exp(-0.5 * x * x)));
            THRESHOLD[i + 1] = (x / previous) * SCALE;
            previous = x;
            HEIGHT[i] = Math.exp(-0.5 * x * x);
            WIDTH[i] = x / SCALE;
        }
    }

    // First half of the state.
    private long state0;
    // Second half of the state.
    private long state1;

    // Creates a source with a fresh seed.
    public FastRandom() {
        super(SEEDS.getAndAdd(GOLDEN));
    }

    // Creates a source with the seed.
    public FastRandom(long seed) {
        super(seed);
    }

    // Returns the shared source of the particles.
    public static FastRandom particles() {
        return PARTICLES;
    }

    // Resets the state from the seed.
    @Override
    public void setSeed(long seed) {
        long z = seed + GOLDEN;
        this.state0 = mix(z);
        this.state1 = mix(z + GOLDEN);
        if (this.state0 == 0L && this.state1 == 0L) {
            this.state0 = GOLDEN;
        }
    }

    // Returns the mixed bits of the value.
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // Returns the next random long.
    @Override
    public long nextLong() {
        long s0 = this.state0;
        long s1 = this.state1;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        this.state0 = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
        this.state1 = Long.rotateLeft(s1, 28);
        return result;
    }

    // Returns the next random bits.
    @Override
    protected int next(int bits) {
        return bits <= 0 ? 0 : (int) (this.nextLong() >>> (64 - bits));
    }

    // Returns the next random int.
    @Override
    public int nextInt() {
        return (int) (this.nextLong() >>> 32);
    }

    // Returns the next random boolean.
    @Override
    public boolean nextBoolean() {
        return this.nextLong() < 0L;
    }

    // Returns the next random float between 0 and 1.
    @Override
    public float nextFloat() {
        return (this.nextLong() >>> 40) * 0x1.0p-24F;
    }

    // Returns the next random double between 0 and 1.
    @Override
    public double nextDouble() {
        return (this.nextLong() >>> 11) * 0x1.0p-53D;
    }

    // Returns the next normally distributed value.
    @Override
    public double nextGaussian() {
        for (;;) {
            int sample = (int) (this.nextLong() >>> 32);
            int layer = sample & (LAYERS - 1);
            long magnitude = sample < 0 ? -(long) sample : sample;
            if (magnitude < THRESHOLD[layer]) {
                return sample * WIDTH[layer];
            }

            double x = sample * WIDTH[layer];
            if (layer == 0) {
                double dx;
                double dy;
                do {
                    dx = -Math.log(1.0 - this.nextDouble()) / TAIL;
                    dy = -Math.log(1.0 - this.nextDouble());
                } while (dy + dy < dx * dx);
                return sample > 0 ? TAIL + dx : -TAIL - dx;
            }

            if (HEIGHT[layer] + this.nextDouble() * (HEIGHT[layer - 1] - HEIGHT[layer]) < Math.exp(-0.5 * x * x)) {
                return x;
            }
        }
    }
}
