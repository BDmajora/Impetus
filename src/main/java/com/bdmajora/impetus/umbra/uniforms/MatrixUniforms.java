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
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The camera-matrix uniforms (gbufferModelView/Projection and inverses, the gbufferPrevious* pair, cameraPosition/previousCameraPosition), all read from CapturedRenderingState and CameraUniforms rather than computed here
public final class MatrixUniforms {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // One report per uniform name, since a singular matrix repeats every frame until its cause goes away
    private static final java.util.Set<String> REPORTED_SINGULAR = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Matrix4fc IDENTITY = new Matrix4f();
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    private static final int GL_MATRIX_MODE = 0x0BA0;
    private static final int GL_TEXTURE_MODE = 0x1702;
    private static final int GL_TEXTURE_MATRIX = 0x0BA8;
    private static final int GL_MODELVIEW_MATRIX = 0x0BA6;
    private static final Matrix4fc LIGHTMAP_TEXTURE_MATRIX = new Matrix4f(
            0.00390625f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.00390625f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.00390625f, 0.0f,
            0.03125f, 0.03125f, 0.03125f, 1.0f);
    // One scratch buffer for the fixed-function readbacks below; render thread only. These ran per DYNAMIC uniform per program bind, and each allocateDirect zeroes memory and registers a Cleaner, thousands of times a frame in an entity-heavy scene
    private static final FloatBuffer SCRATCH = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    // The live modelview and its inverse, read and inverted once per DYNAMIC walk (ProgramUniforms.dynamicPass) and shared by the five suppliers that want them
    private static final Matrix4f LIVE_MODELVIEW = new Matrix4f();
    // Owned by the DYNAMIC-walk cache below, so it must not share the transient inverse scratch
    private static final Matrix4f LIVE_MODELVIEW_INVERSE = new Matrix4f();
    private static Matrix4fc liveModelViewInverse = IDENTITY;
    // Render-thread scratch for every supplier whose answer MatrixUniform uploads at once; a fresh 64-byte matrix per inverse per program per frame added up to thousands of allocations a second
    private static final Matrix4f INVERSE_SCRATCH = new Matrix4f();
    private static final Matrix4f MVP_SCRATCH = new Matrix4f();
    private static final Matrix4f TEXTURE_SCRATCH = new Matrix4f();
    private static final Matrix3f NORMAL_SCRATCH = new Matrix3f();
    private static final Vector3f VECTOR_SCRATCH = new Vector3f();
    private static int liveModelViewPass = -1;
    private static int liveModelViewInversePass = -1;

    private MatrixUniforms() {
    }

    // gbuffer, shadow and previous-frame matrices with their inverses
    public static void addMatrixUniforms(UniformCollector uniforms) {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        uniforms
                // The pack-facing pair is re-based so the camera sits at the player-space origin (Iris 1.17+ convention) rather than at eyeHeight above it (OptiFine 1.12's, which put every "ray from the origin" a modern pack marches — Complementary's light shafts — at the player's feet); the iris_ vanilla stand-ins keep the raw capture since 1.12 geometry is submitted feet-relative
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferModelView",
                        state::getGbufferModelViewCameraCentered)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ModelViewMatrix", state::getGbufferModelView)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ModelViewMat", state::getGbufferModelView)
                // Plain inverse of the re-based matrix, no translation surgery; its translation column is the camera offset, which is what ViewToPlayer needs to land in camera-relative space
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferModelViewInverse",
                        () -> invertedOrIdentity("gbufferModelViewInverse", state.getGbufferModelViewCameraCentered()))
                // Stand-ins for `gl_ModelViewMatrixInverse`, a different family from `gbufferModelViewInverse`: Umbra uploads these PER DRAW from the pose stack, and deriving them from gbufferModelView keeps only the camera half so every entity loses its own model transform (same defect as the normal matrix below)
                .uniformMatrix(UniformUpdateFrequency.DYNAMIC, "iris_ModelViewMatrixInverse",
                        MatrixUniforms::getLiveModelViewInverse)
                .uniformMatrix(UniformUpdateFrequency.DYNAMIC, "iris_ModelViewMatInverse",
                        MatrixUniforms::getLiveModelViewInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferProjection", state::getGbufferProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjectionMatrix", state::getGbufferProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjMat", state::getGbufferProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "gbufferProjectionInverse",
                        () -> invertedOrIdentity("gbufferProjectionInverse", state.getGbufferProjection()))
                // The projection Distant Horizons' LODs rendered with (camera FOV, DH near/far), the camera's own without DH; a pack unprojects dhDepthTex0 with these
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhProjection", DhCompat::getProjection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhProjectionInverse",
                        () -> invertedOrIdentity("dhProjectionInverse", DhCompat.getProjection()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhPreviousProjection",
                        new Previous(DhCompat::getProjection))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "dhPreviousProjectionInverse",
                        new Previous(() -> invertedOrIdentity("dhPreviousProjectionInverse", DhCompat.getProjection())))
                // Umbra swaps the shadow projection in here during the shadow pass (ExtendedShader#umbra$setupState); these are the `gl_ProjectionMatrixInverse` stand-ins, so a shadow program must invert the shadow ortho or depth unprojection yields garbage, while the pack-facing `gbufferProjectionInverse` stays camera-only by definition
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjectionMatrixInverse",
                        MatrixUniforms::getActiveProjectionInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "iris_ProjMatInverse",
                        MatrixUniforms::getActiveProjectionInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, "u_ModelViewProjectionMatrix",
                        () -> MVP_SCRATCH.set(state.getGbufferProjection()).mul(state.getGbufferModelView()))
                // Umbra computes the normal matrix PER DRAW from that draw's modelview (camera AND model transform); deriving it from gbufferModelView keeps only the camera half so every entity loses its rotation, and PER_FRAME shares one value across every object. DYNAMIC + the live fixed-function modelview is the 1.12 equivalent, as iris_TextureMat already does
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
                // Re-based like gbufferModelView: the pack reconstructs `position = shadowModelViewInverse * shadowProjectionInverse * ftransform()` from feet-relative geometry, so this pair yields camera-relative positions, and `shadowProjection * shadowModelView * position` lands on the same clip position the raw matrices produced
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
                // Umbra's precise camera-position split (CameraUniforms): integer and fractional parts computed in DOUBLE on the CPU so the fraction stays exact anywhere; Complementary's voxelization uses cameraPositionBestFract under IRIS_VERSION, and the OptiFine float `fract(cameraPosition)` path made colored lighting shimmer along block edges far from origin
                .uniform3i(UniformUpdateFrequency.PER_FRAME, "cameraPositionInt",
                        () -> CameraUniforms.getCameraPositionInt(CameraUniforms.getCurrentCameraPositionUnshifted()))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "cameraPositionFract",
                        () -> CameraUniforms.getCameraPositionFract(CameraUniforms.getCurrentCameraPositionUnshifted()))
                .uniform3i(UniformUpdateFrequency.PER_FRAME, "previousCameraPositionInt",
                        () -> CameraUniforms.getCameraPositionInt(CameraUniforms.getPreviousCameraPositionUnshifted()))
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "previousCameraPositionFract",
                        () -> CameraUniforms.getCameraPositionFract(CameraUniforms.getPreviousCameraPositionUnshifted()));
    }

    // Double to float
    private static Vector3f toVector3f(Vector3d position) {
        return VECTOR_SCRATCH.set((float) position.x, (float) position.y, (float) position.z);
    }

    // Inverts a matrix, substituting identity when the result is non-finite: Iris inverts unguarded off an always-invertible pose stack, but this port feeds fixed-function readbacks that can be singular (an all-zero buffer captured early, a degenerate ortho), and JOML's invert() yields Inf/NaN silently; Complementary routes EVERY terrain vertex through these inverses, so one NaN deleted the world while entities kept drawing. Identity is wrong but FINITE, and the log names the uniform
    private static Matrix4fc invertedOrIdentity(String name, Matrix4fc source) {
        return invertedOrIdentity(name, source, INVERSE_SCRATCH);
    }

    // The same guard writing into a caller-owned matrix, for the one result that outlives its supplier call
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

    // Rejects NaN and infinity before upload, which a degenerate projection can produce
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

    // The inverse of the modelview live RIGHT NOW, this draw's matrix; Iris's per-draw equivalent reads the pose stack (camera times model), which on the compatibility profile is exactly GL_MODELVIEW_MATRIX at draw time. Inverted once per DYNAMIC walk
    private static Matrix4fc getLiveModelViewInverse() {
        int pass = ProgramUniforms.dynamicPass();
        if (pass != liveModelViewInversePass) {
            liveModelViewInverse = invertedOrIdentity("iris_ModelViewMatrixInverse", getLiveModelView(), LIVE_MODELVIEW_INVERSE);
            liveModelViewInversePass = pass;
        }
        return liveModelViewInverse;
    }

    // The fixed-function modelview as it stands at this instant: camera times model for the draw in progress; queried once per DYNAMIC walk, since nothing changes the matrix between the uniforms of one bind
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

    // The inverse of whichever projection the running pass rasterises with; the shadow pass uses its own ortho, so the camera projection would be wrong there
    private static Matrix4fc getActiveProjectionInverse() {
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        boolean shadow = UmbraShadowRenderer.isShadowPass();
        Matrix4fc projection = shadow ? state.getShadowProjection() : state.getGbufferProjection();
        return invertedOrIdentity(shadow ? "iris_ProjectionMatrixInverse (shadow ortho)"
                : "iris_ProjectionMatrixInverse (camera projection)", projection);
    }

    // A non-finite normal matrix cannot delete geometry but poisons every lit fragment, so it shares the guarded inverse above (the transpose of the inverse's upper 3x3)
    private static Matrix3fc getLiveNormalMatrix() {
        return getLiveModelViewInverse().transpose3x3(NORMAL_SCRATCH);
    }

    // Identity, for packs reading gl_TextureMatrix outside a textured draw
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

    // Supplies the PREVIOUS frame's value of a matrix; get() runs once per PROGRAM declaring the uniform, and rolling on every call collapsed `previous` onto current so TAA reprojection saw zero motion and smeared, so the roll is guarded on frameCounter
    private static final class Previous implements Supplier<Matrix4fc> {
        private final Supplier<Matrix4fc> parent;
        private final Matrix4f previous = new Matrix4f();
        private final Matrix4f current = new Matrix4f();
        private int lastFrame = -1;

        private Previous(Supplier<Matrix4fc> parent) {
            this.parent = parent;
        }

        // Previous-frame supplier; advances when sampled
        @Override
        public Matrix4fc get() {
            int frame = SystemTimeUniforms.COUNTER.getFrameCounter();
            if (frame != this.lastFrame) {
                this.previous.set(this.current);
                this.current.set(this.parent.get());
                if (this.lastFrame == -1) {
                    // First frame: no genuine history yet, so avoid a bogus one-frame jump.
                    this.previous.set(this.current);
                }
                this.lastFrame = frame;
            }
            // The uniform uploads it before the next roll, so no copy is needed
            return this.previous;
        }
    }
}
