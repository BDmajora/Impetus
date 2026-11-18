package com.bdmajora.extras.client.culling;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import net.minecraft.util.math.MathHelper;

// Worker thread that ray-casts which entities and block entities the camera can see.
public final class OcclusionCullingThread extends Thread {
    // Shortest time between the starts of two passes.
    private static final long PASS_INTERVAL_NANOS = 8_000_000L;

    // Running thread or null.
    private static volatile OcclusionCullingThread instance;

    // Opacity of the loaded blocks.
    private final OcclusionGrid grid = new OcclusionGrid();
    // Visibility tester over the grid.
    private final OcclusionTester tester = new OcclusionTester(this.grid);
    // Snapshot the worker tests or null before the first one.
    private OcclusionSnapshot snapshot;

    // Duration of the last pass.
    public static volatile long lastPassNanos;
    // Entities tested by the last pass.
    public static volatile int lastEntities;
    // Entities hidden by the last pass.
    public static volatile int lastEntitiesHidden;
    // Block entities tested by the last pass.
    public static volatile int lastBlockEntities;
    // Block entities hidden by the last pass.
    public static volatile int lastBlockEntitiesHidden;
    // Rays cast by the last pass.
    public static volatile int lastRays;

    // Creates the thread.
    private OcclusionCullingThread() {
        super("Impetus Occlusion Culling");
        this.setDaemon(true);
        this.setPriority(Thread.MIN_PRIORITY + 1);
    }

    // Starts the thread when it is not running.
    public static void ensureRunning() {
        if (instance == null) {
            synchronized (OcclusionCullingThread.class) {
                if (instance == null) {
                    OcclusionCullingThread thread = new OcclusionCullingThread();
                    thread.start();
                    instance = thread;
                }
            }
        }
    }

    // Returns the grid of the worker.
    OcclusionGrid grid() {
        return this.grid;
    }

    // Runs a pass every interval until interrupted.
    @Override
    public void run() {
        while (true) {
            long start = System.nanoTime();
            try {
                ExtrasConfig.OcclusionSettings settings = Extras.options().occlusion;
                if (settings.enabled) {
                    this.pass(settings);
                    lastPassNanos = System.nanoTime() - start;
                } else {
                    this.idle();
                }
            } catch (Throwable ignored) {
                this.tester.raycaster().forgetSection();
            }
            long sleep = PASS_INTERVAL_NANOS - (System.nanoTime() - start);
            try {
                if (sleep > 0L) {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } else if (Thread.interrupted()) {
                    return;
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // Applies the queued grid changes and lets go of the entities while switched off.
    void idle() {
        OcclusionInbox.Batch batch = OcclusionCulling.INBOX.drain();
        if (batch != null) {
            this.grid.apply(batch);
        }
        OcclusionCulling.EXCHANGE.take(null);
        this.snapshot = null;
    }

    // Applies the queued grid changes and tests every target of the newest snapshot.
    void pass(ExtrasConfig.OcclusionSettings settings) {
        OcclusionInbox.Batch batch = OcclusionCulling.INBOX.drain();
        if (batch != null) {
            this.grid.apply(batch);
        }
        this.snapshot = OcclusionCulling.EXCHANGE.take(this.snapshot);
        OcclusionCamera camera = OcclusionCulling.camera;
        OcclusionSnapshot targets = this.snapshot;
        if (camera == null || targets == null) {
            return;
        }
        long now = System.nanoTime();
        long hideDelay = settings.hideDelay * 1_000_000L;
        this.tester.begin(camera.x(), camera.y(), camera.z(), settings.raycastSlack / 100.0D, settings.maxDistance);
        // Draws every target while the camera sits inside an opaque block.
        boolean blind = this.grid.isOpaque(MathHelper.floor(camera.x()), MathHelper.floor(camera.y()), MathHelper.floor(camera.z()));
        int entities = 0;
        int entitiesHidden = 0;
        int blockEntities = 0;
        int blockEntitiesHidden = 0;
        for (int i = 0, n = targets.count(); i < n; i++) {
            boolean visible = blind || this.test(targets, i);
            boolean hidden = settle(targets.target(i), visible, now, hideDelay);
            if (targets.kind(i) == OcclusionSnapshot.ENTITY) {
                entities++;
                entitiesHidden += hidden ? 1 : 0;
            } else {
                blockEntities++;
                blockEntitiesHidden += hidden ? 1 : 0;
            }
        }
        lastEntities = entities;
        lastEntitiesHidden = entitiesHidden;
        lastBlockEntities = blockEntities;
        lastBlockEntitiesHidden = blockEntitiesHidden;
        lastRays = this.tester.rays();
    }

    // Returns whether a target is visible and counts it visible when the racy block read fails.
    private boolean test(OcclusionSnapshot targets, int slot) {
        try {
            return this.tester.isVisible(targets.bound(slot, 0), targets.bound(slot, 1), targets.bound(slot, 2),
                    targets.bound(slot, 3), targets.bound(slot, 4), targets.bound(slot, 5));
        } catch (RuntimeException e) {
            this.tester.raycaster().forgetSection();
            return true;
        }
    }

    // Applies the hide delay to a raw verdict and stores the result.
    static boolean settle(Cullable target, boolean visible, long now, long hideDelayNanos) {
        if (visible) {
            target.impetus$setHiddenSince(0L);
            target.impetus$setOccluded(false, now);
            return false;
        }
        long since = target.impetus$hiddenSince();
        if (since == 0L) {
            since = now == 0L ? 1L : now;
            target.impetus$setHiddenSince(since);
        }
        boolean occluded = now - since >= hideDelayNanos;
        target.impetus$setOccluded(occluded, now);
        return occluded;
    }
}
