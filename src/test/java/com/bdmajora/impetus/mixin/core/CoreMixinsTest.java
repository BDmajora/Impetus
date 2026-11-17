package com.bdmajora.impetus.mixin.core;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.mixin.ImpetusVintageMixinPlugin;
import com.bdmajora.impetus.mixin.core.collections.ClassInheritanceMultiMapMixin;
import com.bdmajora.impetus.mixin.core.crash.SplashProgressCallableMixin;
import com.bdmajora.impetus.mixin.core.frustum.ClippingHelperImplMixin;
import com.bdmajora.impetus.mixin.core.frustum.FrustumMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.bdmajora.impetus.impl.platform.GameWindow;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.FloatBuffer;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreMixinsTest {
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private ImpetusGameOptions previousOptions;
    private ImpetusGameOptions options;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void settle() throws ClassNotFoundException {
        Mc.client();
        Class.forName(ImpetusVintage.class.getName());
        previousOptions = Statics.get(ImpetusVintage.class, "CONFIG");
        options = ImpetusGameOptions.defaults();
        Statics.set(ImpetusVintage.class, "CONFIG", options);
        Display.reset();
    }

    @AfterEach
    void restore() {
        Statics.set(ImpetusVintage.class, "CONFIG", previousOptions);
        Display.reset();
        ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.AFK;
    }

    @Test
    void frustumTestsRunAgainstTheJomlCopyOfVanillasPlanes() {
        // Vanilla's clipping helper with identity matrices sees the unit cube around the camera
        ClippingHelperImplMixin helper = Mixins.instance(ClippingHelperImplMixin.class);
        Mixins.set(helper, "projectionMatrix", IDENTITY.clone());
        Mixins.set(helper, "modelviewMatrix", IDENTITY.clone());
        Mixins.call(helper, "updateJoml", Mixins.ci());
        assertTrue(helper.impetus$getJomlFrustum().testAab(-0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F));
        assertFalse(helper.impetus$getJomlFrustum().testAab(4, 4, 4, 5, 5, 5));

        FrustumMixin frustum;
        // Frustum's own constructor reads the live GL matrices into the shared helper
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class)) {
            frustum = Mixins.instance(FrustumMixin.class);
        }
        Mixins.set(frustum, "clippingHelper", helper);
        Mixins.set(frustum, "x", 10.0);
        Mixins.set(frustum, "y", 20.0);
        Mixins.set(frustum, "z", 30.0);
        // Checks boxes relative to the frustum position.
        assertTrue(frustum.isBoxInFrustum(9.5, 19.5, 29.5, 10.5, 20.5, 30.5));
        assertFalse(frustum.isBoxInFrustum(0, 0, 0, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(Double.NEGATIVE_INFINITY, 0, 0, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, Double.NEGATIVE_INFINITY, 0, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, Double.NEGATIVE_INFINITY, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, 0, 1, Double.POSITIVE_INFINITY, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, 0, 1, 1, Double.POSITIVE_INFINITY));
        // Checks the viewport position.
        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        modelView.clear();
        modelView.put(IDENTITY).flip();
        Viewport viewport = frustum.impetus$createViewport();
        assertEquals(10.0, viewport.getTransform().x, 1e-6);
        assertEquals(20.0, viewport.getTransform().y, 1e-6);
        assertEquals(30.0, viewport.getTransform().z, 1e-6);

        // Checks a mod's own ClippingHelper.
        List<double[]> asked = new ArrayList<>();
        net.minecraft.client.renderer.culling.ClippingHelper plain = new net.minecraft.client.renderer.culling.ClippingHelper() {
            @Override
            public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
                asked.add(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
                return minX < 0;
            }
        };
        Mixins.set(frustum, "clippingHelper", plain);
        assertTrue(frustum.isBoxInFrustum(9.5, 19.5, 29.5, 10.5, 20.5, 30.5));
        assertArrayEquals(new double[] {-0.5, -0.5, -0.5, 0.5, 0.5, 0.5}, asked.get(0), 1e-9);
        assertFalse(frustum.isBoxInFrustum(11, 20, 30, 12, 21, 31));
        Viewport fallback = frustum.impetus$createViewport();
        assertEquals(10.0, fallback.getTransform().x, 1e-6);
        assertEquals(2, asked.size());
    }

    @Test
    void theGlInfoCrashSectionNeedsACurrentContext() {
        SplashProgressCallableMixin callable = Mixins.instance(SplashProgressCallableMixin.class);
        try (MockedStatic<GameWindow> window = Mockito.mockStatic(GameWindow.class)) {
            CallbackInfoReturnable<String> notCurrent = Mixins.cir();
            Mixins.call(callable, "checkContext", notCurrent);
            assertEquals("No context available", notCurrent.getReturnValue());
            window.when(GameWindow::isContextCurrent).thenReturn(true);
            CallbackInfoReturnable<String> current = Mixins.cir();
            Mixins.call(callable, "checkContext", current);
            assertFalse(current.isCancelled());
            // A window that is torn down mid-report is treated as no context
            window.when(GameWindow::isContextCurrent).thenThrow(new IllegalStateException("gone"));
            CallbackInfoReturnable<String> lost = Mixins.cir();
            Mixins.call(callable, "checkContext", lost);
            assertEquals("No context available", lost.getReturnValue());
        }
    }

    @Test
    void theInventoryModelDrawsUnderVanillasDepthStateWhateverTheCacheBelieves() {
        assertNotNull(Mixins.instance(GuiInventoryDepthMixin.class));
        List<String> calls = new ArrayList<>();
        try (MockedStatic<GlStateManager> gl = Mockito.mockStatic(GlStateManager.class)) {
            gl.when(GlStateManager::disableDepth).thenAnswer(call -> record(calls, "disableDepth"));
            gl.when(GlStateManager::enableDepth).thenAnswer(call -> record(calls, "enableDepth"));
            gl.when(() -> GlStateManager.depthMask(anyBoolean())).thenAnswer(call -> record(calls, "depthMask " + call.getArgument(0)));
            gl.when(() -> GlStateManager.depthFunc(anyInt())).thenAnswer(call -> record(calls, "depthFunc " + call.getArgument(0)));
            Mixins.call(GuiInventoryDepthMixin.class, "impetus$restoreDepthState", 51, 75, 30, 0.0F, 0.0F, null, Mixins.ci());
        }
        // Checks every final GL state matches vanilla.
        assertEquals(List.of("disableDepth", "enableDepth", "depthMask false", "depthMask true",
                "depthFunc " + GL11.GL_ALWAYS, "depthFunc " + GL11.GL_LEQUAL), calls);
        verify(TestGl.gl()).glDepthRange(0.0, 1.0);
    }

    private static Object record(List<String> calls, String call) {
        calls.add(call);
        return null;
    }

    @Test
    void entityListsIterateWithoutAnIterator() {
        @SuppressWarnings("unchecked")
        ClassInheritanceMultiMapMixin<String> map = Mixins.instance(ClassInheritanceMultiMapMixin.class);
        Mixins.set(map, "values", new ArrayList<>(List.of("zombie", "creeper")));
        List<String> seen = new ArrayList<>();
        map.forEach(seen::add);
        assertEquals(List.of("zombie", "creeper"), seen);
    }

    @Test
    void theFacingOfAVectorIsItsLongestAxis() {
        assertNotNull(Mixins.construct(MixinDirection.class));
        assertEquals(EnumFacing.NORTH, MixinDirection.getFacingFromVector(0, 0, 0));
        assertEquals(EnumFacing.UP, MixinDirection.getFacingFromVector(0.1F, 1, 0.1F));
        assertEquals(EnumFacing.DOWN, MixinDirection.getFacingFromVector(0, -1, 0));
        // Checks ties go to Y then Z then X.
        assertEquals(EnumFacing.UP, MixinDirection.getFacingFromVector(1, 1, 1));
        assertEquals(EnumFacing.SOUTH, MixinDirection.getFacingFromVector(0.5F, 0, 0.5F));
        assertEquals(EnumFacing.NORTH, MixinDirection.getFacingFromVector(0.2F, 0.1F, -0.5F));
        assertEquals(EnumFacing.EAST, MixinDirection.getFacingFromVector(1, 0.5F, 0.5F));
        assertEquals(EnumFacing.WEST, MixinDirection.getFacingFromVector(-1, 0.5F, 0.5F));
        // Checks a NaN normal answers NORTH.
        assertEquals(EnumFacing.NORTH, MixinDirection.getFacingFromVector(Float.NaN, Float.NaN, Float.NaN));
        assertEquals(EnumFacing.NORTH, MixinDirection.getFacingFromVector(Float.NaN, 1, 0));
        assertEquals(EnumFacing.NORTH, MixinDirection.getFacingFromVector(Float.MIN_VALUE, 0, -Float.MIN_VALUE));
        // Checks every finite vector against vanilla's loop.
        float[] values = {0.0F, -0.0F, Float.MIN_VALUE, -Float.MIN_VALUE, 2 * Float.MIN_VALUE, 1e-30F, -1e-30F, 0.25F, -0.25F, 0.5F, -0.5F,
                1.0F, -1.0F, 3.0F, -3.0F, Float.MAX_VALUE, -Float.MAX_VALUE, Float.NaN};
        for (float x : values) {
            for (float y : values) {
                for (float z : values) {
                    assertEquals(vanillaFacing(x, y, z), MixinDirection.getFacingFromVector(x, y, z), x + ", " + y + ", " + z);
                }
            }
        }
    }

    // EnumFacing.getFacingFromVector as 1.12 ships it
    private static EnumFacing vanillaFacing(float x, float y, float z) {
        EnumFacing best = EnumFacing.NORTH;
        float most = Float.MIN_VALUE;
        for (EnumFacing facing : EnumFacing.values()) {
            float dot = x * facing.getDirectionVec().getX() + y * facing.getDirectionVec().getY() + z * facing.getDirectionVec().getZ();
            if (dot > most) {
                most = dot;
                best = facing;
            }
        }
        return best;
    }

    @Test
    void theGameLoopIsFencedAndThrottled() {
        MinecraftMixin minecraft = Mixins.instance(MinecraftMixin.class);
        when(TestGl.gl().glFenceSync(anyInt(), anyInt())).thenReturn(11L, 12L, 0L);
        // Queues frames past the limit.
        options.advanced.cpuRenderAheadLimit = 1;
        Mixins.call(minecraft, "preRender", Mixins.ci());
        Mixins.call(minecraft, "postRender", Mixins.ci());
        Mixins.call(minecraft, "preRender", Mixins.ci());
        Mixins.call(minecraft, "postRender", Mixins.ci());
        verify(TestGl.gl(), never()).glClientWaitSync(anyLong(), anyInt(), anyLong());
        options.advanced.cpuRenderAheadLimit = 0;
        Mixins.call(minecraft, "preRender", Mixins.ci());
        verify(TestGl.gl()).glClientWaitSync(eq(11L), anyInt(), eq(Long.MAX_VALUE));
        verify(TestGl.gl()).glDeleteSync(11L);
        verify(TestGl.gl()).glDeleteSync(12L);
        // A driver that cannot make a fence is an error
        assertThrows(RuntimeException.class, () -> Mixins.call(minecraft, "postRender", Mixins.ci()));

        // Checks the inactivity caps.
        try (MockedStatic<GameWindow> window = Mockito.mockStatic(GameWindow.class)) {
            ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.NO_LIMIT;
            window.when(GameWindow::isMinimized).thenReturn(true);
            assertEquals(144, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            assertEquals(0, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 0));
            ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.AFK;
            assertEquals(10, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            assertEquals(5, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 5));
            assertEquals(1, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 1));
            // Checks a limit of zero or less.
            assertEquals(10, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 0));
            assertEquals(10, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", -1));
            assertEquals(10, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", Integer.MIN_VALUE));
            window.when(GameWindow::isMinimized).thenReturn(false);
            assertEquals(30, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            assertEquals(30, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 0));
            assertEquals(30, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", -7));
            assertEquals(20, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 20));
            window.when(GameWindow::isFocused).thenReturn(true);
            assertEquals(144, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            assertEquals(0, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 0));
            ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.MINIMIZED;
            window.when(GameWindow::isFocused).thenReturn(false);
            assertEquals(144, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
        }
    }

    // Returns the plugin defined from the main classes alone.
    private static IMixinConfigPlugin jarPlugin() throws ReflectiveOperationException {
        URL main = ImpetusVintageMixinPlugin.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader app = CoreMixinsTest.class.getClassLoader();
        String name = ImpetusVintageMixinPlugin.class.getName();
        ClassLoader jar = new ClassLoader(app) {
            @Override
            protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                if (!className.equals(name)) {
                    return super.loadClass(className, resolve);
                }
                synchronized (getClassLoadingLock(className)) {
                    Class<?> loaded = findLoadedClass(className);
                    if (loaded != null) {
                        return loaded;
                    }
                    try (InputStream in = app.getResourceAsStream(className.replace('.', '/') + ".class")) {
                        byte[] bytes = in.readAllBytes();
                        // Defines the class with a code source.
                        return defineClass(className, bytes, 0, bytes.length, new ProtectionDomain(new CodeSource(main, (Certificate[]) null), null));
                    } catch (IOException e) {
                        throw new ClassNotFoundException(className, e);
                    }
                }
            }

            @Override
            public URL getResource(String resource) {
                try {
                    return main.toURI().resolve(resource).toURL();
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new AssertionError(e);
                }
            }
        };
        return (IMixinConfigPlugin) Class.forName(name, true, jar).getDeclaredConstructor().newInstance();
    }

    @Test
    void thePluginFindsEveryMixinOnItsOwn() throws ReflectiveOperationException {
        ImpetusVintageMixinPlugin plugin = new ImpetusVintageMixinPlugin();
        plugin.onLoad("com.bdmajora.impetus.mixin");
        assertNull(plugin.getRefMapperConfig());
        assertFalse(plugin.shouldApplyMixin("a", "b"));
        plugin.acceptTargets(java.util.Set.of(), java.util.Set.of());
        plugin.preApply("a", null, "b", null);
        plugin.postApply("a", null, "b", null);
        List<String> mixins = jarPlugin().getMixins();
        assertTrue(mixins.contains("core.MinecraftMixin"), mixins.toString());
        assertTrue(mixins.contains("core.frustum.FrustumMixin"));
    }
}
