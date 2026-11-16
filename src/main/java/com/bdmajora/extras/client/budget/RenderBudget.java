package com.bdmajora.extras.client.budget;

// Limits that the render budget currently applies.
public final class RenderBudget {
    // Budget that limits nothing.
    public static final RenderBudget NEUTRAL = new RenderBudget(1.0, Integer.MAX_VALUE, Integer.MAX_VALUE, false, false, 16.7, 0.0, 0.0, false, BottleneckDetector.State.UNKNOWN);

    // Share of cosmetic particles that stay.
    public final double particleScale;
    // Distance beyond which entities may be skipped.
    public final int entityCullDistance;
    // Distance beyond which block entities may be skipped.
    public final int blockEntityCullDistance;
    // Whether distant item frames may be skipped.
    public final boolean itemFramesLimited;
    // Whether limits apply right now.
    public final boolean armed;
    // Target frame time.
    public final double targetFrameMillis;
    // Smoothed frame time.
    public final double emaFrameMillis;
    // Share by which the frame time exceeds the target.
    public final double framePressure;
    // Whether adaptive limits apply.
    public final boolean adaptiveActive;
    // State of the bottleneck detector.
    public final BottleneckDetector.State bottleneck;

    // Creates a budget.
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

    // Returns whether particles are limited.
    public boolean limitsParticles() {
        return this.particleScale < 1.0;
    }

    // Returns whether entities are limited.
    public boolean limitsEntities() {
        return this.entityCullDistance != Integer.MAX_VALUE;
    }

    // Returns whether block entities are limited.
    public boolean limitsBlockEntities() {
        return this.blockEntityCullDistance != Integer.MAX_VALUE;
    }
}
