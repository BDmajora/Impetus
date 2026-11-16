package com.bdmajora.impetus.umbra.uniforms;

import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Render state captured during a frame for uniforms.
public final class CapturedRenderingState {
    // Shared instance.
    public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

    // Gbuffer model view.
    private final Matrix4f gbufferModelView = new Matrix4f();
    // Gbuffer projection.
    private final Matrix4f gbufferProjection = new Matrix4f();
    // Camera position.
    private final Vector3d cameraPosition = new Vector3d();
    // Fog color.
    private final Vector3f fogColor = new Vector3f();
    // Shadow model view.
    private final Matrix4f shadowModelView = new Matrix4f();
    // Shadow projection.
    private final Matrix4f shadowProjection = new Matrix4f();
    // Camera offset.
    private final Vector3d cameraOffset = new Vector3d();
    // Previous camera offset.
    private final Vector3d previousCameraOffset = new Vector3d();
    // Gbuffer model view with the camera at the origin.
    private final Matrix4f gbufferModelViewCameraCentered = new Matrix4f();
    // Shadow model view with the camera at the origin.
    private final Matrix4f shadowModelViewCameraCentered = new Matrix4f();
    // Atlas size.
    private final Vector2i atlasSize = new Vector2i();
    // Color modulator.
    private final Vector4f colorModulator = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    // Entity color.
    private final Vector4f entityColor = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    // Partial tick of the frame.
    private float tickDelta;
    // Render stage.
    private int renderStage;
    // Current alpha test.
    private float currentAlphaTest;
    // Id of the block entity being drawn or minus one.
    private int currentRenderedBlockEntity = -1;
    // Id of the entity being drawn or minus one.
    private int currentRenderedEntity = -1;
    // Id of the item being drawn or minus one.
    private int currentRenderedItem = -1;
    // Number of texture reloads.
    private int textureReloadCount;

    // Prevents instantiation
    private CapturedRenderingState() {
    }

    // Returns the gbuffer model view.
    public Matrix4f getGbufferModelView() {
        return this.gbufferModelView;
    }

    // Stores the gbuffer model view.
    public void setGbufferModelView(Matrix4f modelView) {
        this.gbufferModelView.set(modelView);
        this.previousCameraOffset.set(this.cameraOffset);
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

    // Returns the gbuffer model view camera centered.
    public Matrix4f getGbufferModelViewCameraCentered() {
        return this.gbufferModelViewCameraCentered;
    }

    // Returns the shadow model view camera centered.
    public Matrix4f getShadowModelViewCameraCentered() {
        return this.shadowModelViewCameraCentered;
    }

    // Returns the camera offset.
    public Vector3d getCameraOffset() {
        return this.cameraOffset;
    }

    // Returns the previous camera offset.
    public Vector3d getPreviousCameraOffset() {
        return this.previousCameraOffset;
    }

    // Returns the gbuffer projection.
    public Matrix4f getGbufferProjection() {
        return this.gbufferProjection;
    }

    // Stores the gbuffer projection.
    public void setGbufferProjection(Matrix4f projection) {
        this.gbufferProjection.set(projection);
    }

    // Returns the camera position.
    public Vector3d getCameraPosition() {
        return this.cameraPosition;
    }

    // Stores the camera position.
    public void setCameraPosition(double x, double y, double z) {
        this.cameraPosition.set(x, y, z);
    }

    // Returns the shadow model view.
    public Matrix4f getShadowModelView() {
        return this.shadowModelView;
    }

    // Stores the shadow model view.
    public void setShadowModelView(Matrix4f modelView) {
        this.shadowModelView.set(modelView);
        this.shadowModelViewCameraCentered.set(modelView)
                .translate((float) this.cameraOffset.x, (float) this.cameraOffset.y, (float) this.cameraOffset.z);
    }

    // Returns the shadow projection.
    public Matrix4f getShadowProjection() {
        return this.shadowProjection;
    }

    // Stores the shadow projection.
    public void setShadowProjection(Matrix4f projection) {
        this.shadowProjection.set(projection);
    }

    // Returns the atlas size.
    public Vector2i getAtlasSize() {
        return this.atlasSize;
    }

    // Stores the atlas size.
    public void setAtlasSize(int width, int height) {
        this.atlasSize.set(width, height);
    }

    // Returns the fog color.
    public Vector3f getFogColor() {
        return this.fogColor;
    }

    // Stores the fog color.
    public void setFogColor(float red, float green, float blue) {
        this.fogColor.set(red, green, blue);
    }

    // Returns the color modulator.
    public Vector4f getColorModulator() {
        return this.colorModulator;
    }

    // Stores the color modulator.
    public void setColorModulator(float red, float green, float blue, float alpha) {
        this.colorModulator.set(red, green, blue, alpha);
    }

    // Returns the entity color.
    public Vector4f getEntityColor() {
        return this.entityColor;
    }

    // Stores the entity color.
    public void setEntityColor(float red, float green, float blue, float alpha) {
        this.entityColor.set(red, green, blue, alpha);
    }

    // Clears the entity color.
    public void resetEntityColor() {
        this.entityColor.set(0.0f, 0.0f, 0.0f, 0.0f);
    }

    // Returns the render stage.
    public int getRenderStage() {
        return this.renderStage;
    }

    // Stores the render stage.
    public void setRenderStage(int renderStage) {
        this.renderStage = renderStage;
    }

    // Returns the tick delta.
    public float getTickDelta() {
        return this.tickDelta;
    }

    // Stores the tick delta.
    public void setTickDelta(float tickDelta) {
        this.tickDelta = tickDelta;
    }

    // Returns the current alpha test.
    public float getCurrentAlphaTest() {
        return this.currentAlphaTest;
    }

    // Stores the current alpha test.
    public void setCurrentAlphaTest(float currentAlphaTest) {
        this.currentAlphaTest = currentAlphaTest;
    }

    // Returns the current rendered block entity.
    public int getCurrentRenderedBlockEntity() {
        return this.currentRenderedBlockEntity;
    }

    // Stores the current rendered block entity.
    public void setCurrentRenderedBlockEntity(int id) {
        this.currentRenderedBlockEntity = id;
    }

    // Returns the current rendered entity.
    public int getCurrentRenderedEntity() {
        return this.currentRenderedEntity;
    }

    // Stores the current rendered entity.
    public void setCurrentRenderedEntity(int id) {
        this.currentRenderedEntity = id;
    }

    // Returns the current rendered item.
    public int getCurrentRenderedItem() {
        return this.currentRenderedItem;
    }

    // Stores the current rendered item.
    public void setCurrentRenderedItem(int id) {
        this.currentRenderedItem = id;
    }

    // Returns the texture reload count.
    public int getTextureReloadCount() {
        return this.textureReloadCount;
    }

    // Counts a texture reload.
    public void incrementTextureReloadCount() {
        this.textureReloadCount++;
    }
}
