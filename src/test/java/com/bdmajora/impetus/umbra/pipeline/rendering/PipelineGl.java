package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The GL a rendering test runs against: GL11/13/14 statics mocked, every texture unit and sampler binding modelled whichever path bound it (GlStateManager's cached one or a raw LWJGL call), the bound program tracked for GL_CURRENT_PROGRAM, and a 1.12 client at 320x240
public final class PipelineGl implements AutoCloseable {
    public static final int GL_CURRENT_PROGRAM = 0x8B8D;
    public static final int UNITS = 64;

    public final int[] textures = new int[UNITS];
    public final int[] samplers = new int[UNITS];
    public final Minecraft client;
    public final GameSettings settings;
    public final MockedStatic<GL11> gl11;
    public final MockedStatic<GL13> gl13;
    public final MockedStatic<GL14> gl14;
    public int activeUnit;
    public int program;
    private final boolean framebufferSupported = OpenGlHelper.framebufferSupported;
    private final int defaultTexUnit = OpenGlHelper.defaultTexUnit;
    private final int lightmapTexUnit = OpenGlHelper.lightmapTexUnit;

    public static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    public PipelineGl() {
        this.client = Mc.client();
        this.settings = Mc.uninitialized(GameSettings.class);
        this.settings.renderDistanceChunks = 8;
        this.settings.fboEnable = true;
        Mixins.set(this.client, "gameSettings", this.settings);
        this.client.displayWidth = 320;
        this.client.displayHeight = 240;
        when(this.client.getFramebuffer()).thenReturn(mock(Framebuffer.class));
        TextureManager textureManager = mock(TextureManager.class);
        ITextureObject atlas = mock(ITextureObject.class);
        when(atlas.getGlTextureId()).thenReturn(3);
        when(textureManager.getTexture(any())).thenReturn(atlas);
        when(this.client.getTextureManager()).thenReturn(textureManager);
        OpenGlHelper.framebufferSupported = true;
        OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
        OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE0 + 1;
        // The shadow pass enters the engine's managed scope, whose device must exist but start inactive
        Devices.active().makeInactive();
        this.gl11 = Mockito.mockStatic(GL11.class);
        this.gl13 = Mockito.mockStatic(GL13.class);
        this.gl14 = Mockito.mockStatic(GL14.class);
        this.gl13.when(() -> GL13.glActiveTexture(anyInt())).thenAnswer(inv -> {
            this.activeUnit = inv.<Integer>getArgument(0) - GL13.GL_TEXTURE0;
            return null;
        });
        this.gl11.when(() -> GL11.glBindTexture(anyInt(), anyInt())).thenAnswer(inv -> {
            this.textures[this.activeUnit] = inv.getArgument(1);
            return null;
        });
        LWJGLService gl = TestGl.gl();
        Mockito.doAnswer(inv -> this.activeUnit = inv.<Integer>getArgument(0) - GL13.GL_TEXTURE0).when(gl).glActiveTexture(anyInt());
        Mockito.doAnswer(inv -> this.textures[this.activeUnit] = inv.getArgument(1)).when(gl).glBindTexture(anyInt(), anyInt());
        Mockito.doAnswer(inv -> this.samplers[inv.<Integer>getArgument(0)] = inv.getArgument(1)).when(gl).glBindSampler(anyInt(), anyInt());
        Mockito.doAnswer(inv -> this.program = inv.getArgument(0)).when(gl).glUseProgram(anyInt());
        when(gl.glGetInteger(GL_CURRENT_PROGRAM)).thenAnswer(inv -> this.program);
        when(gl.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)).thenAnswer(inv -> this.textures[this.activeUnit]);
        limits(48, 8, 8, 8);
        when(gl.supportsBufferBlending()).thenReturn(true);
        Mockito.doNothing().when(gl).glEnablei(anyInt(), anyInt());
        Mockito.doNothing().when(gl).glDisablei(anyInt(), anyInt());
        Mockito.doNothing().when(gl).glBlendFuncSeparatei(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        resetStateManager();
    }

    // GL_MAX_TEXTURE_IMAGE_UNITS, GL_MAX_DRAW_BUFFERS, GL_MAX_COLOR_ATTACHMENTS, GL_MAX_IMAGE_UNITS
    public void limits(int textureUnits, int drawBuffers, int attachments, int imageUnits) {
        LWJGLService gl = TestGl.gl();
        when(gl.glGetInteger(0x8872)).thenReturn(textureUnits);
        when(gl.glGetInteger(0x8824)).thenReturn(drawBuffers);
        when(gl.glGetInteger(0x8CDF)).thenReturn(attachments);
        when(gl.glGetInteger(0x8D57)).thenReturn(imageUnits);
        when(gl.glGetInteger(0x90DD)).thenReturn(16);
        when(gl.glGetInteger(0x90DE)).thenReturn(1 << 24);
    }

    // Every program declares every uniform and sampler it is offered, at location 1
    public void allUniforms() {
        when(TestGl.gl().glGetUniformLocation(anyInt(), any(CharSequence.class))).thenReturn(1);
    }

    // GlStateManager's cache back to "unit 0, nothing bound", matching the modelled GL
    public static void resetStateManager() {
        Statics.set(GlStateManager.class, "activeTextureUnit", 0);
        Object[] states = Statics.get(GlStateManager.class, "textureState");
        for (Object state : states) {
            Mixins.set(state, "textureName", 0);
        }
    }

    // A real pipeline over an in-memory pack
    static UmbraRenderingPipeline pipeline(String... pathsAndSources) {
        return new UmbraRenderingPipeline(PackSourcesTest.pack(pathsAndSources));
    }

    // The state behind a pipeline, whose package-private fields a test may read or swap
    static PipelineState state(UmbraRenderingPipeline pipeline) {
        return Mixins.get(pipeline, "state");
    }

    // Puts a built state into its frame, as beginWorldRendering leaves it, without running the frame start
    static void enterFrame(PipelineState state) {
        state.active = true;
        state.gbuffers.current = state.schedule.gbuffer;
        state.activeGbufferFlips = state.schedule.preTranslucentFlips;
    }

    // What GlStateManager believes is bound on a cached unit
    public static int cachedTexture(int unit) {
        Object[] states = Statics.get(GlStateManager.class, "textureState");
        return Mixins.get(states[unit], "textureName");
    }

    // The cached active unit
    public static int cachedUnit() {
        return Statics.get(GlStateManager.class, "activeTextureUnit");
    }

    @Override
    public void close() {
        this.gl14.close();
        this.gl13.close();
        this.gl11.close();
        OpenGlHelper.framebufferSupported = this.framebufferSupported;
        OpenGlHelper.defaultTexUnit = this.defaultTexUnit;
        OpenGlHelper.lightmapTexUnit = this.lightmapTexUnit;
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Statics.set(UmbraShadowRenderer.class, "shadowPassActive", false);
        resetStateManager();
    }
}
