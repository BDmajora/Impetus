package com.bdmajora.impetus.umbra.uniforms;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.program.ProgramUniforms;
import com.bdmajora.impetus.umbra.gl.uniform.UniformCollector;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.impetus.umbra.gl.uniform.UniformUpdateFrequency;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Matrix and camera position uniforms of the shader packs.
public final class MatrixUniforms {
    // Logger of singular matrices.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Names of the singular matrices already reported.
    private static final Set<String> REPORTED_SINGULAR = ConcurrentHashMap.newKeySet();
    // Identity matrix.
    private static final Matrix4fc IDENTITY = new Matrix4f();
    // GL enum of the active texture unit.
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    // GL enum of the matrix mode.
    private static final int GL_MATRIX_MODE = 0x0BA0;
    // GL enum of the texture matrix mode.
    private static final int GL_TEXTURE_MODE = 0x1702;
    // GL enum of the texture matrix.
    private static final int GL_TEXTURE_MATRIX = 0x0BA8;
    // GL enum of the model view matrix.
    private static final int GL_MODELVIEW_MATRIX = 0x0BA6;
    // Matrix that maps lightmap coordinates to texels.
    private static final Matrix4fc LIGHTMAP_TEXTURE_MATRIX = new Matrix4f(
            0.00390625f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.00390625f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.00390625f, 0.0f,
            0.03125f, 0.03125f, 0.03125f, 1.0f);
    // Scratch of a GL matrix read.
    private static final FloatBuffer SCRATCH = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    // Model view matrix of the current draw.
    private static final Matrix4f LIVE_MODELVIEW = new Matrix4f();
    // Inverse of the model view matrix of the current draw.
    private static final Matrix4f LIVE_MODELVIEW_INVERSE = new Matrix4f();
    // Inverse of the live model view or identity.
    private static Matrix4fc liveModelViewInverse = IDENTITY;
    // Scratch of an inversion.
    private static final Matrix4f INVERSE_SCRATCH = new Matrix4f();
    // Scratch of the model view projection product.
    private static final Matrix4f MVP_SCRATCH = new Matrix4f();
    // Scratch of the texture matrix.
    private static final Matrix4f TEXTURE_SCRATCH = new Matrix4f();
    // Scratch of the normal matrix.
    private static final Matrix3f NORMAL_SCRATCH = new Matrix3f();
    // Scratch of a float position.
    private static final Vector3f VECTOR_SCRATCH = new Vector3f();
    // Update pass of the live model view.
    private static int liveModelViewPass = -1;
    // Update pass of the live model view inverse.
    private static int liveModelViewInversePass = -1;

    // Prevents instantiation
    private MatrixUniforms() {
    }

    // Registers every matrix and camera position uniform.
    public static void addMatrixUniforms(UniformCollector uniforms) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        uniforms
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferModelView",
                        state::getGbufferModelViewCameraCentered)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ModelViewMatrix", state::getGbufferModelView)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ModelViewMat", state::getGbufferModelView)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferModelViewInverse",
                        () -> invertedOrIdentity("gbufferModelViewInverse", state.getGbufferModelViewCameraCentered()))
                .uniformMatrix(UniformUpdateFrequency.DYNAMIC, "iris_ModelViewMatrixInverse",
                        MatrixUniforms::getLiveModelViewInverse)
                .uniformMatrix(UniformUpdateFrequency.DYNAMIC, "iris_ModelViewMatInverse",
                        MatrixUniforms::getLiveModelViewInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferProjection", state::getGbufferProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjectionMatrix", state::getGbufferProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjMat", state::getGbufferProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferProjectionInverse",
                        () -> invertedOrIdentity("gbufferProjectionInverse", state.getGbufferProjection()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhProjection", DhCompat::getProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhProjectionInverse",
                        () -> invertedOrIdentity("dhProjectionInverse", DhCompat.getProjection()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhPreviousProjection",
                        new Previous(DhCompat::getProjection))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhPreviousProjectionInverse",
                        new Previous(() -> invertedOrIdentity("dhPreviousProjectionInverse", DhCompat.getProjection())))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjectionMatrixInverse",
                        MatrixUniforms::getActiveProjectionInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjMatInverse",
                        MatrixUniforms::getActiveProjectionInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "u_ModelViewProjectionMatrix",
                        () -> MVP_SCRATCH.set(state.getGbufferProjection()).mul(state.getGbufferModelView()))
                .uniformMatrix3(UniformUpdateFrequency.DYNAMIC, "iris_DefaultNormalMat",
                        MatrixUniforms::getLiveNormalMatrix)
                .uniformMatrix3(UniformUpdateFrequency.DYNAMIC, "iris_NormalMatrix",
                        MatrixUniforms::getLiveNormalMatrix)
                .uniformMatrix3(UniformUpdateFrequency.DYNAMIC, "iris_NormalMat",
                        MatrixUniforms::getLiveNormalMatrix)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_DefaultModelViewMatrixInverse",
                        () -> invertedOrIdentity("iris_DefaultModelViewMatrixInverse", state.getGbufferModelView()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_DefaultProjectionMatrixInverse",
                        () -> invertedOrIdentity("iris_DefaultProjectionMatrixInverse", state.getGbufferProjection()))
                .uniformMatrix(UniformUpdateFrequency.DYNAMIC, "iris_TextureMat",
                        MatrixUniforms::getDefaultTextureMatrix)
                .uniformMatrix(UniformUpdateFrequency.ONCE, "iris_LightmapTextureMatrix",
                        () -> LIGHTMAP_TEXTURE_MATRIX)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferPreviousModelView",
                        new Previous(state::getGbufferModelViewCameraCentered))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferPreviousProjection",
                        new Previous(state::getGbufferProjection))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "shadowModelView",
                        state::getShadowModelViewCameraCentered)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "shadowModelViewInverse",
                        () -> invertedOrIdentity("shadowModelViewInverse", state.getShadowModelViewCameraCentered()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "shadowProjection", state::getShadowProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "shadowProjectionInverse",
                        () -> invertedOrIdentity("shadowProjectionInverse", state.getShadowProjection()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ShadowModelViewMatrixInverse",
                        () -> invertedOrIdentity("iris_ShadowModelViewMatrixInverse", state.getShadowModelView()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ShadowProjectionMatrixInverse",
                        () -> invertedOrIdentity("iris_ShadowProjectionMatrixInverse", state.getShadowProjection()))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "cameraPosition",
                        () -> toVector3f(CameraUniforms.getCurrentCameraPosition()))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "previousCameraPosition",
                        () -> toVector3f(CameraUniforms.getPreviousCameraPosition()))
                .uniform3i(UniformUpdateFrequency.PER_FRAME, "cameraPositionInt",
                        () -> CameraUniforms.getCameraPositionInt(CameraUniforms.getCurrentCameraPositionUnshifted()))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "cameraPositionFract",
                        () -> CameraUniforms.getCameraPositionFract(CameraUniforms.getCurrentCameraPositionUnshifted()))
                .uniform3i(UniformUpdateFrequency.PER_FRAME, "previousCameraPositionInt",
                        () -> CameraUniforms.getCameraPositionInt(CameraUniforms.getPreviousCameraPositionUnshifted()))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "previousCameraPositionFract",
                        () -> CameraUniforms.getCameraPositionFract(CameraUniforms.getPreviousCameraPositionUnshifted()));
    }

    // Returns a position as floats in a scratch vector.
    private static Vector3f toVector3f(Vector3d position) {
        return VECTOR_SCRATCH.set((float) position.x, (float) position.y, (float) position.z);
    }

    // Returns the inverse of a matrix in the shared scratch or identity for a singular one.
    private static Matrix4fc invertedOrIdentity(String name, Matrix4fc source) {
        return invertedOrIdentity(name, source, INVERSE_SCRATCH);
    }

    // Returns the inverse of a matrix in a destination or identity and reports a singular one once.
    private static Matrix4fc invertedOrIdentity(String name, Matrix4fc source, Matrix4f dest) {
        Matrix4f inverse = dest.set(source).invert();
        if (isFinite(inverse)) {
            return inverse;
        }
        if (REPORTED_SINGULAR.add(name)) {
            LOGGER.error("[Umbra] '{}' inverted to a non-finite matrix; its source is singular, so every vertex "
                    + "transformed by it would be NaN. Substituting identity. Source was:\n{}", name, source);
        }
        return IDENTITY;
    }

    // Returns whether every element is finite.
    private static boolean isFinite(Matrix4fc matrix) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                if (!Float.isFinite(matrix.get(column, row))) {
                    return false;
                }
            }
        }
        return true;
    }

    // Returns the inverse of the live model view once per update pass.
    private static Matrix4fc getLiveModelViewInverse() {
        int pass = ProgramUniforms.dynamicPass();
        if (pass != liveModelViewInversePass) {
            liveModelViewInverse = invertedOrIdentity("iris_ModelViewMatrixInverse", getLiveModelView(), LIVE_MODELVIEW_INVERSE);
            liveModelViewInversePass = pass;
        }
        return liveModelViewInverse;
    }

    // Reads the model view matrix once per update pass.
    private static Matrix4fc getLiveModelView() {
        int pass = ProgramUniforms.dynamicPass();
        if (pass != liveModelViewPass) {
            SCRATCH.clear();
            GlStateManager.getFloat(GL_MODELVIEW_MATRIX, SCRATCH);
            SCRATCH.rewind();
            LIVE_MODELVIEW.set(SCRATCH);
            liveModelViewPass = pass;
        }
        return LIVE_MODELVIEW;
    }

    // Returns the inverse projection of the shadow or camera pass.
    private static Matrix4fc getActiveProjectionInverse() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        boolean shadow = UmbraShadowRenderer.isShadowPass();
        Matrix4fc projection = shadow ? state.getShadowProjection() : state.getGbufferProjection();
        return invertedOrIdentity(shadow ? "iris_ProjectionMatrixInverse (shadow ortho)"
                : "iris_ProjectionMatrixInverse (camera projection)", projection);
    }

    // Returns the normal matrix of the live model view.
    private static Matrix3fc getLiveNormalMatrix() {
        return getLiveModelViewInverse().transpose3x3(NORMAL_SCRATCH);
    }

    // Reads the texture matrix of the default unit and restores the unit and matrix mode.
    private static Matrix4fc getDefaultTextureMatrix() {
        int previousTexture = LWJGL.glGetInteger(GL_ACTIVE_TEXTURE);
        int previousMatrixMode = LWJGL.glGetInteger(GL_MATRIX_MODE);
        try {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.matrixMode(GL_TEXTURE_MODE);
            SCRATCH.clear();
            GlStateManager.getFloat(GL_TEXTURE_MATRIX, SCRATCH);
            SCRATCH.rewind();
            return TEXTURE_SCRATCH.set(SCRATCH);
        } finally {
            GlStateManager.matrixMode(previousMatrixMode);
            GlStateManager.setActiveTexture(previousTexture);
        }
    }

    // Supplier of the value a matrix had on the last frame.
    private static final class Previous implements Supplier<Matrix4fc> {
        // Supplier of the current value.
        private final Supplier<Matrix4fc> parent;
        // Value of the last frame.
        private final Matrix4f previous = new Matrix4f();
        // Value of this frame.
        private final Matrix4f current = new Matrix4f();
        // Frame of the last read.
        private int lastFrame = -1;

        // Creates a supplier of the last frame value.
        private Previous(Supplier<Matrix4fc> parent) {
            this.parent = parent;
        }

        // Returns the value of the last frame and reads the parent once per frame.
        @Override
        public Matrix4fc get() {
            int frame = SystemTimeUniforms.COUNTER.getFrameCounter();
            if (frame != this.lastFrame) {
                this.previous.set(this.current);
                this.current.set(this.parent.get());
                if (this.lastFrame == -1) {
                    this.previous.set(this.current);
                }
                this.lastFrame = frame;
            }
            return this.previous;
        }
    }
}
