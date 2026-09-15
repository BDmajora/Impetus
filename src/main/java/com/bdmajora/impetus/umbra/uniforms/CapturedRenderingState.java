package com.bdmajora.impetus.umbra.uniforms;

import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

// A singleton snapshot of per-frame render state captured out of the vanilla render loop (camera matrices and position, partial ticks, render stage, alpha threshold, the entity or block entity being submitted); a singleton because the capturing mixins are scattered and have no pipeline reference
public final class CapturedRenderingState {
    public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

    private final Matrix4f gbufferModelView = new Matrix4f();
    private final Matrix4f gbufferProjection = new Matrix4f();
    private final Vector3d cameraPosition = new Vector3d();
    private final Vector3f fogColor = new Vector3f();
    private final Matrix4f shadowModelView = new Matrix4f();
    private final Matrix4f shadowProjection = new Matrix4f();
    // Where the camera (the view-space origin) sits relative to vanilla's render origin, the view entity's FEET: 1.12's model-view carries orientCamera's translate(0, -eyeHeight, 0) and the third-person pull-back, so player space as OptiFine 1.12 defined it starts at the feet. Modern packs assume the Iris/1.17+ convention where player space starts AT the camera (Complementary marches its light shafts as `rayDirection * distance` from the origin, so from the feet every downward ray was underground and the fog cut off at the horizon); the pack-facing matrices and cameraPosition below are re-based by this vector while the geometry keeps rendering feet-relative
    private final Vector3d cameraOffset = new Vector3d();
    private final Vector3d previousCameraOffset = new Vector3d();
    // gbufferModelView * translate(cameraOffset): maps camera-relative player space to view space with the camera at the origin, what the gbufferModelView/gbufferModelViewInverse uniforms upload
    private final Matrix4f gbufferModelViewCameraCentered = new Matrix4f();
    // shadowModelView * translate(cameraOffset), the shadowModelView uniform; the shadow pass itself still draws feet-relative geometry through the raw matrix, and the two agree on clip positions since the offset is folded into the vertex position the pack reconstructs
    private final Matrix4f shadowModelViewCameraCentered = new Matrix4f();
    private final Vector2i atlasSize = new Vector2i();
    private final Vector4f colorModulator = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private final Vector4f entityColor = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    private float tickDelta;
    // The renderStage uniform, the current WorldRenderingPhase ordinal matching MC_RENDER_STAGE_*; packs switch on it to tell terrain from entities from the hand within one program
    private int renderStage;
    private float currentAlphaTest;
    private int currentRenderedBlockEntity = -1;
    private int currentRenderedEntity = -1;
    private int currentRenderedItem = -1;
    private int textureReloadCount;

    private CapturedRenderingState() {
    }

    // Model-view captured at the start of the gbuffer stage
    public Matrix4f getGbufferModelView() {
        return this.gbufferModelView;
    }

    // Captured by the renderer mixin once per frame; also rolls the camera offset, so previousCameraOffset pairs with the previous frame's camera position the way CameraUniforms rolls the feet position at frame start
    public void setGbufferModelView(Matrix4f modelView) {
        this.gbufferModelView.set(modelView);
        this.previousCameraOffset.set(this.cameraOffset);
        // The view-space origin pulled back into feet-relative space is exactly the camera point vanilla's gluUnProject reports, bobbing and third-person offsets included; a singular capture (all-zero buffer on the first frames) leaves the offset at zero rather than NaN
        Matrix4f inverse = new Matrix4f(modelView).invert();
        Vector4f origin = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        inverse.transform(origin);
        if (Float.isFinite(origin.x) && Float.isFinite(origin.y) && Float.isFinite(origin.z) && origin.w != 0.0f) {
            this.cameraOffset.set(origin.x / origin.w, origin.y / origin.w, origin.z / origin.w);
        } else {
            this.cameraOffset.set(0.0, 0.0, 0.0);
        }
        this.gbufferModelViewCameraCentered.set(modelView)
                .translate((float) this.cameraOffset.x, (float) this.cameraOffset.y, (float) this.cameraOffset.z);
        this.shadowModelViewCameraCentered.set(this.shadowModelView)
                .translate((float) this.cameraOffset.x, (float) this.cameraOffset.y, (float) this.cameraOffset.z);
    }

    // The pack-facing model-view with the camera at the player-space origin (Iris convention)
    public Matrix4f getGbufferModelViewCameraCentered() {
        return this.gbufferModelViewCameraCentered;
    }

    // The pack-facing shadow model-view, re-based the same way
    public Matrix4f getShadowModelViewCameraCentered() {
        return this.shadowModelViewCameraCentered;
    }

    // Camera point relative to the feet render origin this frame
    public Vector3d getCameraOffset() {
        return this.cameraOffset;
    }

    // Same for the previous frame, for previousCameraPosition
    public Vector3d getPreviousCameraOffset() {
        return this.previousCameraOffset;
    }

    // Projection captured at the start of the gbuffer stage
    public Matrix4f getGbufferProjection() {
        return this.gbufferProjection;
    }

    // Captured by the renderer mixin
    public void setGbufferProjection(Matrix4f projection) {
        this.gbufferProjection.set(projection);
    }

    // Camera position this frame
    public Vector3d getCameraPosition() {
        return this.cameraPosition;
    }

    // Captured by the renderer mixin
    public void setCameraPosition(double x, double y, double z) {
        this.cameraPosition.set(x, y, z);
    }

    // Shadow pass model-view
    public Matrix4f getShadowModelView() {
        return this.shadowModelView;
    }

    // Captured by the shadow renderer, after the camera capture of the same frame, so the re-based copy uses this frame's offset
    public void setShadowModelView(Matrix4f modelView) {
        this.shadowModelView.set(modelView);
        this.shadowModelViewCameraCentered.set(modelView)
                .translate((float) this.cameraOffset.x, (float) this.cameraOffset.y, (float) this.cameraOffset.z);
    }

    // Shadow pass projection
    public Matrix4f getShadowProjection() {
        return this.shadowProjection;
    }

    // Captured by the shadow renderer
    public void setShadowProjection(Matrix4f projection) {
        this.shadowProjection.set(projection);
    }

    // Block atlas size, for atlasSize
    public Vector2i getAtlasSize() {
        return this.atlasSize;
    }

    // Captured on stitch
    public void setAtlasSize(int width, int height) {
        this.atlasSize.set(width, height);
    }

    // Fog colour this frame
    public Vector3f getFogColor() {
        return this.fogColor;
    }

    // Captured from vanilla's fog setup
    public void setFogColor(float red, float green, float blue) {
        this.fogColor.set(red, green, blue);
    }

    // GL colour multiplier this draw
    public Vector4f getColorModulator() {
        return this.colorModulator;
    }

    // Captured from GlStateManager.color
    public void setColorModulator(float red, float green, float blue, float alpha) {
        this.colorModulator.set(red, green, blue, alpha);
    }

    // OptiFine's entityColor (rgb tint, a blend factor, the pack finishes with mix(color.rgb, entityColor.rgb, entityColor.a)); a uniform because vanilla paints the hurt flash and creeper charge with fixed-function combiners a bound program ignores
    public Vector4f getEntityColor() {
        return this.entityColor;
    }

    // Hurt or flash tint for the entity being drawn
    public void setEntityColor(float red, float green, float blue, float alpha) {
        this.entityColor.set(red, green, blue, alpha);
    }

    // Clears after the entity
    public void resetEntityColor() {
        this.entityColor.set(0.0f, 0.0f, 0.0f, 0.0f);
    }

    // Iris render stage enum ordinal
    public int getRenderStage() {
        return this.renderStage;
    }

    // Set as the frame progresses
    public void setRenderStage(int renderStage) {
        this.renderStage = renderStage;
    }

    // Partial ticks this frame
    public float getTickDelta() {
        return this.tickDelta;
    }

    // Captured at frame start
    public void setTickDelta(float tickDelta) {
        this.tickDelta = tickDelta;
    }

    // Alpha test threshold for the current draw
    public float getCurrentAlphaTest() {
        return this.currentAlphaTest;
    }

    // Captured from GlStateManager.alphaFunc
    public void setCurrentAlphaTest(float currentAlphaTest) {
        this.currentAlphaTest = currentAlphaTest;
    }

    // Pack id of the tile entity being drawn, or -1
    public int getCurrentRenderedBlockEntity() {
        return this.currentRenderedBlockEntity;
    }

    // Set by the tile entity dispatcher mixin
    public void setCurrentRenderedBlockEntity(int id) {
        this.currentRenderedBlockEntity = id;
    }

    // Pack id of the entity being drawn, or -1
    public int getCurrentRenderedEntity() {
        return this.currentRenderedEntity;
    }

    // Set by the entity renderer mixin
    public void setCurrentRenderedEntity(int id) {
        this.currentRenderedEntity = id;
    }

    // Pack id of the item being drawn, or -1
    public int getCurrentRenderedItem() {
        return this.currentRenderedItem;
    }

    // Set by the item renderer mixin
    public void setCurrentRenderedItem(int id) {
        this.currentRenderedItem = id;
    }

    // Bumped on every resource reload, so caches keyed on it invalidate
    public int getTextureReloadCount() {
        return this.textureReloadCount;
    }

    // Called on reload
    public void incrementTextureReloadCount() {
        this.textureReloadCount++;
    }
}
