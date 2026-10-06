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
import java.net.URL;
import java.nio.FloatBuffer;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
        // Boxes are tested relative to the frustum's position, and unbounded ones always pass
        assertTrue(frustum.isBoxInFrustum(9.5, 19.5, 29.5, 10.5, 20.5, 30.5));
        assertFalse(frustum.isBoxInFrustum(0, 0, 0, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(Double.NEGATIVE_INFINITY, 0, 0, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, Double.NEGATIVE_INFINITY, 0, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, Double.NEGATIVE_INFINITY, 1, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, 0, 1, Double.POSITIVE_INFINITY, 1));
        assertTrue(frustum.isBoxInFrustum(0, 0, 0, 1, 1, Double.POSITIVE_INFINITY));
        // The viewport sits at the frustum's position, moved by the third-person offset (none, with an identity view)
        FloatBuffer modelView = Statics.get(ActiveRenderInfo.class, "MODELVIEW");
        modelView.clear();
        modelView.put(IDENTITY).flip();
        Viewport viewport = frustum.impetus$createViewport();
        assertEquals(10.0, viewport.getTransform().x, 1e-6);
        assertEquals(20.0, viewport.getTransform().y, 1e-6);
        assertEquals(30.0, viewport.getTransform().z, 1e-6);
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
    void entityListsIterateWithoutAnIterator() {
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
        // Ties go to Y, then Z, then X
        assertEquals(EnumFacing.UP, MixinDirection.getFacingFromVector(1, 1, 1));
        assertEquals(EnumFacing.SOUTH, MixinDirection.getFacingFromVector(0.5F, 0, 0.5F));
        assertEquals(EnumFacing.NORTH, MixinDirection.getFacingFromVector(0.2F, 0.1F, -0.5F));
        assertEquals(EnumFacing.EAST, MixinDirection.getFacingFromVector(1, 0.5F, 0.5F));
        assertEquals(EnumFacing.WEST, MixinDirection.getFacingFromVector(-1, 0.5F, 0.5F));
    }

    @Test
    void theGameLoopIsFencedAndThrottled() {
        MinecraftMixin minecraft = Mixins.instance(MinecraftMixin.class);
        when(TestGl.gl().glFenceSync(anyInt(), anyInt())).thenReturn(11L, 12L, 0L);
        // Each frame leaves a fence; once more are queued than the limit allows the oldest is waited on
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

        // Inactivity caps the frame rate: hard when minimised, softer when merely unfocused in AFK mode
        try (MockedStatic<GameWindow> window = Mockito.mockStatic(GameWindow.class)) {
            ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.NO_LIMIT;
            window.when(GameWindow::isMinimized).thenReturn(true);
            assertEquals(144, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.AFK;
            assertEquals(10, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            assertEquals(5, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 5));
            window.when(GameWindow::isMinimized).thenReturn(false);
            assertEquals(30, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            window.when(GameWindow::isFocused).thenReturn(true);
            assertEquals(144, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
            ImpetusRuntimeOptions.inactivityFpsLimit = ImpetusGameOptions.InactivityFpsLimit.MINIMIZED;
            window.when(GameWindow::isFocused).thenReturn(false);
            assertEquals(144, (int) Mixins.call(minecraft, "impetus$applyInactivityFpsLimit", 144));
        }
    }

    // The plugin defined from the main classes alone, as it is inside the mod jar; on the test classpath its package scan would otherwise find the test classes of the same package first
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
                        // With a code source, as a jar class has; JaCoCo leaves classes without one uninstrumented
                        return defineClass(className, bytes, 0, bytes.length, new ProtectionDomain(new CodeSource(main, (Certificate[]) null), null));
                    } catch (IOException e) {
                        throw new ClassNotFoundException(className, e);
                    }
                }
            }

            @Override
            public URL getResource(String resource) {
                try {
                    return new URL(main, resource);
                } catch (MalformedURLException e) {
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
