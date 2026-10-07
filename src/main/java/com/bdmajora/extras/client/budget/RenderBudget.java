package com.bdmajora.extras.client.budget;

// One tick's resolved limits: the fraction of cosmetic particles allowed to spawn and the distances past which idle mobs, block entities and item frames may be skipped; NEUTRAL means "do nothing"
public final class RenderBudget {
    public static final RenderBudget NEUTRAL = new RenderBudget(1.0, Integer.MAX_VALUE, Integer.MAX_VALUE, false, false, 16.7, 0.0, 0.0, false, BottleneckDetector.State.UNKNOWN);

    public final double particleScale;
    public final int entityCullDistance;
    // Already includes the shader-pack bonus; MAX_VALUE when block entities are not budgeted
    public final int blockEntityCullDistance;
    public final boolean itemFramesLimited;
    // Whether skipping is live this tick; the distances are still reported when it is not so the overlay can show what would apply under pressure
    public final boolean armed;
    public final double targetFrameMillis;
    public final double emaFrameMillis;
    // (ema - target) / target clamped at zero, so 0.25 means frames run a quarter over target
    public final double framePressure;
    // Whether the adaptive step tightened this budget past the configured values
    public final boolean adaptiveActive;
    // The detector's verdict the adaptive step followed
    public final BottleneckDetector.State bottleneck;

    RenderBudget(double particleScale, int entityCullDistance, int blockEntityCullDistance, boolean itemFramesLimited,
                 boolean armed, double targetFrameMillis, double emaFrameMillis, double framePressure, boolean adaptiveActive,
                 BottleneckDetector.State bottleneck) {
        this.particleScale = particleScale;
        this.entityCullDistance = entityCullDistance;
        this.blockEntityCullDistance = blockEntityCullDistance;
        this.itemFramesLimited = itemFramesLimited;
        this.armed = armed;
        this.targetFrameMillis = targetFrameMillis;
        this.emaFrameMillis = emaFrameMillis;
        this.framePressure = framePressure;
        this.adaptiveActive = adaptiveActive;
        this.bottleneck = bottleneck;
    }

    // Whether any cosmetic particle can be refused under this budget
    public boolean limitsParticles() {
        return this.particleScale < 1.0;
    }

    // Whether entity skipping is configured at all, armed or not
    public boolean limitsEntities() {
        return this.entityCullDistance != Integer.MAX_VALUE;
    }

    // Whether block entity skipping is configured at all, armed or not
    public boolean limitsBlockEntities() {
        return this.blockEntityCullDistance != Integer.MAX_VALUE;
    }
}
