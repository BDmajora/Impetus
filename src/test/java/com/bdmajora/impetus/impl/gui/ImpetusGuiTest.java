package com.bdmajora.impetus.impl.gui;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.ControlValueFormatter;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.options.CommonOptionPages;
import com.bdmajora.impetus.engine.impl.gui.ImpetusVideoOptionsController;
import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.widgets.SearchBarWidget;
import com.bdmajora.impetus.engine.impl.notification.ImpetusNotifications;
import com.bdmajora.impetus.impl.compat.fluidlogged.FluidloggedCompat;
import com.bdmajora.impetus.impl.gui.overlay.ImpetusToastRenderer;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import com.bdmajora.testing.OptionsScreens;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import com.bdmajora.impetus.impl.platform.GameWindow;
import com.cleanroommc.client.sdl.video.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImpetusGuiTest {
    @TempDir
    static Path home;

    // The option pages capture the client and the Impetus options when they first load, so one of each serves every test here
    private static Minecraft client;
    private static GameSettings settings;
    private static ImpetusGameOptions options;

    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private MockedStatic<GameWindow> display;
    private MockedStatic<Mouse> mouse;
    private MockedStatic<Keyboard> keyboard;

    enum Mode implements Localized {
        CALM, BUSY;

        @Override
        public String translationKey() {
            return "impetus.test.mode." + name().toLowerCase();
        }
    }

    static final class Holder {
        boolean flag;
        int level;
        Mode mode = Mode.CALM;
    }

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
        Mc.forge();
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        settings.renderDistanceChunks = 8;
        settings.gammaSetting = 0.5F;
        settings.limitFramerate = 120;
        settings.clouds = 2;
        settings.fancyGraphics = true;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 854;
        client.displayHeight = 480;
        options = OptionsScreens.prepare(client, home);
        // Sidebar clicks play vanilla's button sound
        when(client.getSoundHandler()).thenReturn(mock(SoundHandler.class));
    }

    @AfterAll
    static void forgetOptions() {
        OptionsScreens.forget();
    }

    @BeforeEach
    void mockNatives() {
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        display = Mockito.mockStatic(GameWindow.class);
        mouse = Mockito.mockStatic(Mouse.class);
        keyboard = Mockito.mockStatic(Keyboard.class);
        Statics.set(FullscreenResolutions.class, "modes", null);
        display.when(GameWindow::fullscreenModes).thenReturn(new DisplayMode[0]);
        // The client, font and texture manager are shared by every test here
        Mockito.clearInvocations(client, client.fontRenderer, client.getTextureManager());
    }

    @AfterEach
    void releaseNatives() {
        // Null-safe: a mock that failed to open in setup must not keep the ones before it registered for every later suite
        for (MockedStatic<?> mock : java.util.Arrays.asList(keyboard, mouse, display, gl13, gl11)) {
            if (mock != null && !mock.isClosed()) {
                mock.close();
            }
        }
        client.world = null;
        Statics.<List<?>>get(ImpetusNotifications.class, "NOTIFICATIONS").clear();
        Mixins.set(Loader.instance(), "namedMods", new HashMap<>());
    }

    private static DisplayMode[] modes() {
        return new DisplayMode[] {
                new DisplayMode(1, 0, 1280, 720, 1, 60),
                new DisplayMode(1, 0, 1920, 1080, 1, 60),
                new DisplayMode(1, 0, 1920, 1080, 1, 144)};
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(OptionPage page, int index, T value) {
        Option<T> option = (Option<T>) page.getOptions().get(index);
        option.setValue(value);
        option.applyChanges();
    }

    @Test
    void theGeneralAndQualityPagesBindEveryOption() throws Exception {
        boolean fluidlogged = Statics.get(FluidloggedCompat.class, "IS_LOADED");
        Statics.set(FluidloggedCompat.class, "IS_LOADED", true);
        try {
            display.when(GameWindow::fullscreenModes).thenReturn(modes());
            var graphicsApi = CommonOptionPages.graphicsApi(ImpetusGameOptionPages.getSodiumOpts().getData());
            OptionPage general = ImpetusGameOptionPages.general(graphicsApi);
            OptionPage quality = ImpetusGameOptionPages.quality();
            OptionPage fluidlogging = ImpetusGameOptionPages.fluidlogging();
            // The General page carries the screen's Graphics API option itself, so the Nvidium page sees the same pending value
            assertSame(graphicsApi, general.getOptions().get(10));
            // The fluidlogging guess has a page of its own rather than sitting among Quality's fluid options
            assertEquals("impetus:fluidlogging", fluidlogging.getId().toString());
            assertEquals(1, fluidlogging.getOptions().size());
            assertEquals(28, OptionPages.exercise(List.of(general, quality)));
            assertEquals(1, OptionPages.exercise(List.of(fluidlogging)));
            // Whichever client was current when the pages first loaded is the one they stay bound to
            ImpetusGameOptions sodium = ImpetusGameOptionPages.getSodiumOpts().getData();
            GameSettings vanilla = ImpetusGameOptionPages.getVanillaOpts().getData();
            assertNotNull(new ImpetusGameOptionPages());

            // 1.12 has one distance for both, so simulation distance moves render distance
            set(general, 1, 12);
            assertEquals(12, vanilla.renderDistanceChunks);
            set(general, 2, 30);
            assertEquals(0.3F, vanilla.gammaSetting, 1e-6F);
            set(general, 3, 2);
            assertEquals(2, vanilla.guiScale);
            verify(client, Mockito.atLeastOnce()).resize(854, 480);
            // Exclusive goes through vanilla's toggle, and a window that refuses leaves the option off
            when(client.isFullScreen()).thenReturn(false);
            set(general, 4, ImpetusGameOptions.FullscreenMode.EXCLUSIVE);
            verify(client).toggleFullscreen();
            assertEquals(ImpetusGameOptions.FullscreenMode.OFF, sodium.fullscreenMode);
            // Borderless from exclusive leaves exclusive first, then asks SDL for desktop fullscreen
            var borderless = new java.util.concurrent.atomic.AtomicBoolean();
            display.when(GameWindow::isBorderless).thenAnswer(inv -> borderless.get());
            display.when(() -> GameWindow.setBorderless(true)).thenAnswer(inv -> {
                borderless.set(true);
                return null;
            });
            when(client.isFullScreen()).thenReturn(true, false);
            set(general, 4, ImpetusGameOptions.FullscreenMode.BORDERLESS);
            verify(client, times(2)).toggleFullscreen();
            display.verify(() -> GameWindow.setBorderless(true));
            assertEquals(ImpetusGameOptions.FullscreenMode.BORDERLESS, sodium.fullscreenMode);
            set(general, 5, 1);
            assertEquals(1, sodium.fullscreenResolution);
            // VSync is the Extras cycler, which keeps vanilla's flag in step
            set(general, 6, com.bdmajora.extras.ExtrasConfig.VerticalSync.ON);
            display.verify(() -> GameWindow.setVsync(true));
            assertTrue(net.minecraft.client.Minecraft.getMinecraft().gameSettings.enableVsync);

            // Improved transparency needs per-face translucency sorting
            sodium.performance.useTranslucentFaceSorting = false;
            set(quality, 0, true);
            assertTrue(sodium.performance.useTranslucentFaceSorting);
            // See-through leaves reach every leaf block, which caches the setting
            set(quality, 4, false);
            assertFalse((boolean) Mixins.get(Blocks.LEAVES, "leavesFancy"));
            set(quality, 4, true);
            assertTrue((boolean) Mixins.get(Blocks.LEAVES2, "leavesFancy"));
            set(quality, 6, true);
            assertEquals(2, vanilla.ambientOcclusion);
            set(quality, 6, false);
            assertEquals(0, vanilla.ambientOcclusion);
        } finally {
            Statics.set(FluidloggedCompat.class, "IS_LOADED", fluidlogged);
        }
    }

    @Test
    void theFrameRateSliderReadsEveryLimitTheGameDoesNotApplyAsUnlimited() {
        OptionPage general = ImpetusGameOptionPages.general(CommonOptionPages.graphicsApi(ImpetusGameOptionPages.getSodiumOpts().getData()));
        @SuppressWarnings("unchecked")
        Option<Integer> fps = (Option<Integer>) general.getOptions().stream()
                .filter(option -> option.getId().toString().equals(com.bdmajora.impetus.api.options.structure.StandardOptions.Option.MAX_FRAMERATE.toString()))
                .findFirst().orElseThrow();
        GameSettings vanilla = ImpetusGameOptionPages.getVanillaOpts().getData();
        int saved = vanilla.limitFramerate;
        try {
            // Vanilla limits only below 260 and sync skips zero or less, so all of these sit at the unlimited end
            for (int unlimited : new int[]{0, -1, 261, 300, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                vanilla.limitFramerate = unlimited;
                assertEquals(260, fps.getValue());
                assertFalse(fps.hasChanged());
                // Untouched, the stored value is left as it was
                fps.applyChanges();
                assertEquals(unlimited, vanilla.limitFramerate);
                // Moving to the unlimited end is no change either
                fps.setValue(260);
                assertFalse(fps.hasChanged());
                fps.reset();
            }
            for (int limited : new int[]{1, 10, 120, 259, 260}) {
                vanilla.limitFramerate = limited;
                assertEquals(limited, fps.getValue());
            }
            // A limit picked from unlimited is written as picked
            vanilla.limitFramerate = 0;
            fps.setValue(120);
            assertTrue(fps.hasChanged());
            fps.applyChanges();
            assertEquals(120, vanilla.limitFramerate);
        } finally {
            fps.reset();
            vanilla.limitFramerate = saved;
        }
    }

    @Test
    void fullscreenResolutionsAreTheMonitorsDistinctModes() throws Exception {
        display.when(GameWindow::fullscreenModes).thenReturn(modes());
        // Current, then the two distinct fullscreen sizes, largest first; SDL exposes only fullscreen modes
        assertEquals(3, FullscreenResolutions.count());
        assertEquals("1920x1080", FullscreenResolutions.label(1));
        assertEquals("1280x720", FullscreenResolutions.label(2));
        assertEquals("impetus.options.fullscreen_resolution.current", FullscreenResolutions.label(0));
        assertEquals("impetus.options.fullscreen_resolution.current", FullscreenResolutions.label(3));
        DisplayMode desktop = new DisplayMode(1, 0, 2560, 1440, 1, 60);
        display.when(GameWindow::desktopMode).thenReturn(desktop);
        FullscreenResolutions.apply(1);
        verify(client, never()).resize(anyInt(), anyInt());
        display.when(() -> GameWindow.setFullscreenMode(any(DisplayMode.class))).thenReturn(true);
        FullscreenResolutions.apply(2);
        verify(client).resize(1280, 720);
        FullscreenResolutions.apply(0);
        verify(client).resize(2560, 1440);
        display.when(GameWindow::desktopMode).thenReturn(null);
        FullscreenResolutions.apply(0);
        display.when(() -> GameWindow.setFullscreenMode(any(DisplayMode.class))).thenThrow(new IllegalStateException("refused"));
        FullscreenResolutions.apply(1);
        assertNotNull(Mixins.construct(FullscreenResolutions.class));

        // A monitor SDL cannot list (headless, or no window yet) offers only the current resolution
        Statics.set(FullscreenResolutions.class, "modes", null);
        display.when(GameWindow::fullscreenModes).thenThrow(new IllegalStateException("no monitor"));
        assertEquals(1, FullscreenResolutions.count());
    }

    @Test
    void moduleOptionsBuildEveryControlFromOneVocabulary() {
        Holder holder = new Holder();
        OptionStorage<Holder> storage = () -> holder;
        ModuleOptions<Holder> module = new ModuleOptions<>("impetus", "test.", "impetus.test.", storage);
        assertSame(storage, module.storage());
        assertEquals(OptionIdentifier.create("impetus", "test.group"), module.group("group"));
        assertEquals(TextComponent.translatable("impetus.test.flag.name"), module.name("flag"));
        assertEquals(TextComponent.translatable("impetus.test.flag.tooltip"), module.tooltip("flag"));

        List<OptionImpl<Holder, ?>> built = new ArrayList<>();
        built.add(module.toggle("flag", (h, v) -> h.flag = v, h -> h.flag, OptionImpact.LOW, OptionFlag.REQUIRES_RENDERER_RELOAD, () -> true));
        built.add(module.toggle("bare", (h, v) -> h.flag = v, h -> h.flag, null, null, null));
        built.add(module.slider("level", 0, 4, 1, ControlValueFormatter.number(), (h, v) -> h.level = v, h -> h.level, OptionImpact.HIGH, null, null));
        built.add(module.cycling("mode", Mode.class, Mode.values(), (h, v) -> h.mode = v, h -> h.mode, () -> false));
        built.add(module.cycling("mode2", Mode.class, Mode.values(), (h, v) -> h.mode = v, h -> h.mode, OptionImpact.MEDIUM, OptionFlag.REQUIRES_ASSET_RELOAD, null));
        built.forEach(OptionPages::exercise);
        assertEquals(OptionIdentifier.create("impetus", "test.flag", boolean.class), built.get(0).getId());
        assertEquals(EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD), built.get(0).getFlags());
        assertFalse(built.get(3).isAvailable());
        assertTrue(built.get(4).isAvailable());
        // The enum cycler shows each value's lang entry
        assertArrayEquals(new TextComponent[] {TextComponent.translatable("impetus.test.mode.calm"), TextComponent.translatable("impetus.test.mode.busy")},
                ModuleOptions.localizedNames(Mode.values()));
        assertEquals("impetus.test.mode.busy", Mode.BUSY.localizedName());
        @SuppressWarnings("unchecked")
        OptionImpl<Holder, Integer> level = (OptionImpl<Holder, Integer>) built.get(2);
        level.setValue(3);
        level.applyChanges();
        assertEquals(3, holder.level);
    }

    @Test
    void vanillaBindingsTakeOnlyOnOffOptions() {
        assertEquals("Option must not be null", assertThrowsExactly(NullPointerException.class, () -> new VanillaBooleanOptionBinding(null)).getMessage());
        for (GameSettings.Options option : GameSettings.Options.values()) {
            if (option.isBoolean()) {
                new VanillaBooleanOptionBinding(option);
            } else {
                // GRAPHICS and the other cycled or slider options would read false forever and step on each write
                assertEquals("Not an on/off option: " + option,
                        assertThrowsExactly(IllegalArgumentException.class, () -> new VanillaBooleanOptionBinding(option)).getMessage());
            }
        }
    }

    @Test
    void vanillaSettingsAreReadAndWrittenThroughTheirOwnMethods() {
        // Vanilla's real setOptionValue and getOptionOrdinalValue; only the options.txt write is stubbed out
        GameSettings real = Mc.spy(GameSettings.class);
        Mockito.doNothing().when(real).saveOptions();
        VanillaBooleanOptionBinding bobbing = new VanillaBooleanOptionBinding(GameSettings.Options.VIEW_BOBBING);
        assertSame(Boolean.FALSE, bobbing.getValue(real));
        bobbing.setValue(real, true);
        assertTrue(real.viewBobbing);
        assertSame(Boolean.TRUE, bobbing.getValue(real));
        // Vanilla flips an on/off option whatever int it is given, so writing the stored value must not reach it, which also spares the options.txt rewrite
        bobbing.setValue(real, true);
        assertTrue(real.viewBobbing);
        verify(real, times(1)).setOptionValue(GameSettings.Options.VIEW_BOBBING, 1);
        verify(real, times(1)).saveOptions();
        bobbing.setValue(real, false);
        bobbing.setValue(real, false);
        assertFalse(real.viewBobbing);
        assertSame(Boolean.FALSE, bobbing.getValue(real));
        verify(real, times(2)).setOptionValue(GameSettings.Options.VIEW_BOBBING, 1);
        verify(real, times(2)).saveOptions();
        // Each binding reads and writes only its own option
        VanillaBooleanOptionBinding invert = new VanillaBooleanOptionBinding(GameSettings.Options.INVERT_MOUSE);
        invert.setValue(real, true);
        assertTrue(real.invertMouse);
        assertTrue(invert.getValue(real));
        assertFalse(bobbing.getValue(real));
        verify(real, never()).setOptionValue(eq(GameSettings.Options.VIEW_BOBBING), eq(0));

        // The storage reads the live settings each time and saves options.txt
        GameSettings vanilla = mock(GameSettings.class);
        MinecraftOptionsStorage storage = new MinecraftOptionsStorage();
        assertSame(settings, storage.getData());
        Mixins.set(client, "gameSettings", vanilla);
        try {
            storage.save(Set.of());
            verify(vanilla).saveOptions();
        } finally {
            Mixins.set(client, "gameSettings", settings);
        }
    }

    @Test
    void toastsStackInTheCornerTrimmedToFit() {
        FontRenderer font = client.fontRenderer;
        ScaledResolution resolution = new ScaledResolution(client);
        // Nothing queued, or toasts turned off, draws nothing
        ImpetusToastRenderer.render(client, resolution);
        ImpetusNotifications.info("Shaders", "compiled");
        options.notifications.showToasts = false;
        ImpetusToastRenderer.render(client, resolution);
        verify(font, never()).drawString(anyString(), anyFloat(), anyFloat(), anyInt(), anyBoolean());
        options.notifications.showToasts = true;

        ImpetusNotifications.warn("A title far too long to fit inside the two hundred and fifty pixel toast at all", "line");
        ImpetusNotifications.error("Failed", "one", "two");
        ImpetusToastRenderer.render(client, resolution);
        // Titles and lines of the three toasts, the long title cut with an ellipsis
        verify(font, times(7)).drawString(anyString(), anyFloat(), anyFloat(), anyInt(), eq(true));
        verify(font).drawString(Mockito.endsWith("..."), anyFloat(), anyFloat(), anyInt(), eq(true));
    }

    @Test
    void theDrawContextCompilesComponentsOntoVanillaText() {
        FontRenderer font = client.fontRenderer;
        VintageDrawContext draw = new VintageDrawContext();
        when(font.drawString(anyString(), anyFloat(), anyFloat(), anyInt(), anyBoolean())).thenReturn(42);
        assertEquals(42, draw.drawString(TextComponent.literal("hi"), 1, 2, 0xFFFFFFFF, true));
        // Translations fall back through their keys to the first, and nest their components
        TextComponent nested = TextComponent.translatable(List.of("missing.key", "other.key"), TextComponent.literal("inner"), 5);
        assertEquals("missing.key", draw.extractString(nested));
        TextComponent styled = TextComponent.literal("warn").withStyle(TextFormattingStyle.RED,
                TextFormattingStyle.STRIKETHROUGH, TextFormattingStyle.UNDERLINE, TextFormattingStyle.ITALIC);
        assertEquals("warn", draw.extractString(styled));
        assertEquals(24, draw.getStringWidth(styled));
        assertEquals("text", draw.extractString(new TextComponent.Styled(TextComponent.literal("text"), EnumSet.noneOf(TextFormattingStyle.class))));
        // Wrapping works on the formatted text, reset code and all
        assertEquals(List.of(TextComponent.literal("wrapped\u00a7r")), draw.split(TextComponent.literal("wrapped"), 40));
        assertEquals("clip", draw.substrByWidth("clip", 10));
        assertEquals(9, draw.lineHeight());
        // An unknown component kind is refused
        assertThrows(IllegalArgumentException.class, () -> draw.extractString(new TextComponent() {
            @Override
            public String toString() {
                return "custom";
            }
        }));

        draw.fill(0, 0, 10, 10, 0xFF00FF00);
        draw.pushMatrix();
        draw.translate(1, 2, 3);
        draw.popMatrix();
        // The scissor box is in window pixels with GL's origin at the bottom
        settings.guiScale = 2;
        draw.enableScissor(10, 20, 29, 39);
        gl11.verify(() -> GL11.glScissor(20, 480 - 80, 40, 40));
        draw.disableScissor();
        gl11.verify(() -> GL11.glDisable(GL11.GL_SCISSOR_TEST));
        draw.blitWholeImage("impetus:textures/gui/impetus.png", 0, 0, 16, 16);
        draw.blitWholeImage("impetus:textures/gui/impetus.png", 0, 0, 16, 16);
        verify(client.getTextureManager(), times(2)).bindTexture(new ResourceLocation("impetus:textures/gui/impetus.png"));
    }

    // A draw sent to GL: its mode, the vertices it read and the colour set before it
    private record GlDraw(int mode, List<Float> vertices, List<Float> color) { }

    @Test
    void anOutlineIsOneDrawOfTheVerticesAndColourThatTheDefaultsFourFillsSend() {
        VintageDrawContext draw = new VintageDrawContext();
        List<GlDraw> draws = new ArrayList<>();
        ByteBuffer[] pointer = new ByteBuffer[1];
        List<Float> color = new ArrayList<>();
        gl11.when(() -> GL11.glColor4f(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenAnswer(invocation -> {
            color.clear();
            for (int i = 0; i < 4; i++) {
                color.add(invocation.<Float>getArgument(i));
            }
            return null;
        });
        gl11.when(() -> GL11.glVertexPointer(anyInt(), anyInt(), anyInt(), any(ByteBuffer.class))).thenAnswer(invocation -> pointer[0] = invocation.getArgument(3));
        // Reads the three floats of each vertex from where the pointer was set, in the buffer's own byte order
        gl11.when(() -> GL11.glDrawArrays(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            ByteBuffer source = pointer[0].duplicate().order(pointer[0].order());
            int count = invocation.getArgument(2);
            List<Float> vertices = new ArrayList<>();
            for (int i = 0; i < count * 3; i++) {
                vertices.add(source.getFloat(source.position() + i * Float.BYTES));
            }
            draws.add(new GlDraw(invocation.getArgument(0), vertices, List.copyOf(color)));
            return null;
        });
        int[][] boxes = {{184, 4, 194, 14}, {0, 0, 200, 18}, {10, 20, 5, 8}, {-30, -40, -10, -2}, {3, 3, 3, 3}, {7, 1, 8, 2}};
        int[] colors = {0xFFFFFFFF, 0x80FF4020, 0x00000000, 0xFF9A9A9A};
        for (int[] box : boxes) {
            for (int argb : colors) {
                String context = java.util.Arrays.toString(box) + " " + Integer.toHexString(argb);
                draws.clear();
                GlStateManager.resetColor();
                draw.drawBorder(box[0], box[1], box[2], box[3], argb);
                assertEquals(1, draws.size(), context);
                GlDraw batched = draws.getFirst();
                draws.clear();
                // The default's four edges, each its own fill
                GlStateManager.resetColor();
                draw.fill(box[0], box[1], box[2], box[1] + 1, argb);
                draw.fill(box[0], box[3] - 1, box[2], box[3], argb);
                draw.fill(box[0], box[1], box[0] + 1, box[3], argb);
                draw.fill(box[2] - 1, box[1], box[2], box[3], argb);
                assertEquals(4, draws.size(), context);
                List<Float> separate = new ArrayList<>();
                for (GlDraw one : draws) {
                    assertEquals(GL11.GL_QUADS, one.mode(), context);
                    assertEquals(batched.color(), one.color(), context);
                    separate.addAll(one.vertices());
                }
                assertEquals(GL11.GL_QUADS, batched.mode(), context);
                assertEquals(16 * 3, batched.vertices().size(), context);
                assertEquals(separate, batched.vertices(), context);
                assertEquals(List.of((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F, (argb >>> 24) / 255.0F), batched.color(), context);
            }
        }
    }

    // Every string the context handed the font to draw since the font's calls were last cleared
    private static List<String> drawnStrings(FontRenderer font) {
        ArgumentCaptor<String> drawn = ArgumentCaptor.forClass(String.class);
        verify(font, Mockito.atLeast(0)).drawString(drawn.capture(), anyFloat(), anyFloat(), anyInt(), anyBoolean());
        return drawn.getAllValues();
    }

    @Test
    void theDrawContextMakesEachComponentsTextOnceAndHandsTheFontTheSameStringEveryFrame() {
        FontRenderer font = client.fontRenderer;
        Mockito.clearInvocations(font);
        VintageDrawContext draw = new VintageDrawContext();
        TextComponent name = TextComponent.literal("Name").withStyle(TextFormattingStyle.WHITE);
        for (int frame = 0; frame < 3; frame++) {
            draw.drawString(name, 0, 0, 0xFFFFFFFF, false);
            // An equal component made afresh each frame finds the same text
            draw.drawString(TextComponent.literal("Name").withStyle(TextFormattingStyle.WHITE), 0, 0, 0xFFFFFFFF, false);
        }
        List<String> strings = drawnStrings(font);
        assertEquals(6, strings.size());
        assertEquals("\u00a7fName\u00a7r", strings.getFirst());
        for (String string : strings) {
            assertSame(strings.getFirst(), string);
        }
        // Measuring and extracting reuse the plain text the same way
        assertSame(draw.extractString(name), draw.extractString(TextComponent.literal("Name").withStyle(TextFormattingStyle.WHITE)));
        assertEquals(24, draw.getStringWidth(name));
    }

    @Test
    void aTextDrawnGreyedKeepsItsGreyOutOfThePlainAndWhiteCopiesOfTheSameText() {
        FontRenderer font = client.fontRenderer;
        Mockito.clearInvocations(font);
        VintageDrawContext draw = new VintageDrawContext();
        TextComponent white = TextComponent.literal("Text").withStyle(TextFormattingStyle.WHITE);
        TextComponent grey = TextComponent.literal("Text").withStyle(TextFormattingStyle.GRAY, TextFormattingStyle.STRIKETHROUGH);
        // A row's value is drawn white, greyed while its option is unavailable, then white again, and a plain copy of it elsewhere
        for (TextComponent text : List.of(white, grey, white, TextComponent.literal("Text"), grey)) {
            draw.drawString(text, 0, 0, 0xFFFFFFFF, false);
        }
        assertEquals(List.of("\u00a7fText\u00a7r", "\u00a77\u00a7mText\u00a7r", "\u00a7fText\u00a7r", "Text\u00a7r", "\u00a77\u00a7mText\u00a7r"), drawnStrings(font));
        // A translation's argument keeps its own style when the same text is also an argument of another
        Map<String, String> server = Mixins.get(Statics.get(net.minecraft.util.text.translation.I18n.class, "localizedName"), "languageList");
        String key = "impetus.test.argument";
        server.put(key, "Value %s");
        try {
            Mockito.clearInvocations(font);
            TextComponent argument = TextComponent.literal("arg");
            TextComponent red = TextComponent.translatable(key, argument.withStyle(TextFormattingStyle.RED));
            draw.drawString(red, 0, 0, 0xFFFFFFFF, false);
            draw.drawString(TextComponent.translatable(key, argument), 0, 0, 0xFFFFFFFF, false);
            draw.drawString(red, 0, 0, 0xFFFFFFFF, false);
            List<String> strings = drawnStrings(font);
            assertTrue(strings.get(0).contains("\u00a7carg"), strings::toString);
            assertFalse(strings.get(1).contains("\u00a7c"), strings::toString);
            assertEquals(strings.get(0), strings.get(2));
        } finally {
            server.remove(key);
        }
    }

    @Test
    void theDrawContextMakesItsTextAgainAfterTheTranslationsReloadAndNotBefore() {
        Map<String, String> client = Mixins.get(Statics.get(net.minecraft.client.resources.I18n.class, "i18nLocale"), "properties");
        Object translations = Statics.get(net.minecraft.util.text.translation.I18n.class, "localizedName");
        Map<String, String> server = Mixins.get(translations, "languageList");
        long stamp = Mixins.get(translations, "lastUpdateTimeInMilliseconds");
        String key = "impetus.test.reload";
        try {
            for (Map<String, String> language : List.of(client, server)) {
                language.put(key, "Plain");
            }
            VintageDrawContext draw = new VintageDrawContext();
            TextComponent text = TextComponent.translatable(List.of(key + ".one", key));
            assertEquals("Plain", draw.extractString(text));
            for (Map<String, String> language : List.of(client, server)) {
                language.put(key + ".one", "Singular");
            }
            // Until the translations reload the text made earlier stands
            assertEquals("Plain", draw.extractString(text));
            // A reload picks the fallback key again, which now finds the singular
            Mixins.set(translations, "lastUpdateTimeInMilliseconds", stamp + 1000);
            assertEquals("Singular", draw.extractString(text));
            assertEquals(48, draw.getStringWidth(text));
        } finally {
            Mixins.set(translations, "lastUpdateTimeInMilliseconds", stamp);
            for (Map<String, String> language : List.of(client, server)) {
                language.remove(key);
                language.remove(key + ".one");
            }
        }
    }

    @Test
    void theDrawContextKeepsNoMoreThanItsLimitOfComponentsWhenTextChangesEveryFrame() {
        VintageDrawContext draw = new VintageDrawContext();
        int limit = Statics.<Integer>get(VintageDrawContext.class, "COMPONENT_CACHE_LIMIT");
        Map<?, ?> cache = Mixins.get(draw, "componentCache");
        for (int frame = 0; frame < limit * 3; frame++) {
            assertEquals("value " + frame, draw.extractString(TextComponent.literal("value " + frame)));
            assertTrue(cache.size() <= limit, () -> "cache holds " + cache.size());
        }
        // Text dropped when the cache started over is made again
        assertEquals("value 0", draw.extractString(TextComponent.literal("value 0")));
    }

    @Test
    void aCountAtOneTakesItsSingularOnlyWhenTheLanguageHasOne() {
        // The client's locale picks the key and the translation map fills it in, which the game keeps in step on every reload
        Map<String, String> client = Mixins.get(Statics.get(net.minecraft.client.resources.I18n.class, "i18nLocale"), "properties");
        Map<String, String> server = Mixins.get(Statics.get(net.minecraft.util.text.translation.I18n.class, "localizedName"), "languageList");
        String key = "impetus.test.count";
        try {
            for (Map<String, String> language : List.of(client, server)) {
                language.put(key, "%s blocks");
                language.put(key + ".one", "%s block");
                language.put(key + ".off", "Off");
            }
            ControlValueFormatter count = ControlValueFormatter.translateQuantity(key);
            VintageDrawContext draw = new VintageDrawContext();
            assertEquals("1 block", draw.extractString(count.format(1)));
            assertEquals("-1 block", draw.extractString(count.format(-1)));
            assertEquals("0 blocks", draw.extractString(count.format(0)));
            assertEquals("2 blocks", draw.extractString(count.format(2)));
            assertEquals("Off", draw.extractString(ControlValueFormatter.translateQuantityOrDisabled(key, key + ".off").format(0)));
            // A language with only the plain form uses it at one as well, rather than showing the singular key
            client.remove(key + ".one");
            server.remove(key + ".one");
            assertEquals("1 blocks", new VintageDrawContext().extractString(count.format(1)));
        } finally {
            for (Map<String, String> language : List.of(client, server)) {
                language.remove(key);
                language.remove(key + ".one");
                language.remove(key + ".off");
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void theDrawContextsTextVersionMovesWithTheTranslationsAndTheFontsModesAndOnlyWithThem() {
        FontRenderer font = client.fontRenderer;
        VintageDrawContext draw = new VintageDrawContext();
        Object translations = Statics.get(net.minecraft.util.text.translation.I18n.class, "localizedName");
        long stamp = Mixins.get(translations, "lastUpdateTimeInMilliseconds");
        try {
            long plain = draw.textVersion();
            assertEquals(plain, draw.textVersion());
            when(font.getUnicodeFlag()).thenReturn(true);
            long unicode = draw.textVersion();
            assertNotEquals(plain, unicode);
            when(font.getBidiFlag()).thenReturn(true);
            long both = draw.textVersion();
            assertNotEquals(unicode, both);
            when(font.getUnicodeFlag()).thenReturn(false);
            assertNotEquals(both, draw.textVersion());
            assertNotEquals(plain, draw.textVersion());
            // A reload stamps the translations with the time it ran
            when(font.getBidiFlag()).thenReturn(false);
            Mixins.set(translations, "lastUpdateTimeInMilliseconds", stamp + 1000);
            assertNotEquals(plain, draw.textVersion());
            assertEquals(net.minecraft.util.text.translation.I18n.getLastTranslationUpdateTimeInMilliseconds() << 2, draw.textVersion());
        } finally {
            Mixins.set(translations, "lastUpdateTimeInMilliseconds", stamp);
            when(font.getUnicodeFlag()).thenReturn(false);
            when(font.getBidiFlag()).thenReturn(false);
        }
    }

    @Test
    void theDrawContextCountsWhatIsPushedAndNestsScissorsInsideEachOther() {
        VintageDrawContext draw = new VintageDrawContext();
        settings.guiScale = 2;
        draw.pushMatrix();
        draw.pushMatrix();
        assertEquals(2, draw.matrixDepth());
        draw.popMatrix();
        draw.popMatrix();
        // An extra pop still reaches GL but never counts below zero
        draw.popMatrix();
        assertEquals(0, draw.matrixDepth());
        gl11.verify(GL11::glPopMatrix, times(3));
        // A box nested in another is clipped to it, and ending it puts the outer box back without turning clipping off
        draw.enableScissor(10, 20, 29, 39);
        gl11.verify(() -> GL11.glScissor(20, 400, 40, 40));
        draw.enableScissor(0, 30, 49, 49);
        gl11.verify(() -> GL11.glScissor(20, 400, 40, 20));
        assertEquals(2, draw.scissorDepth());
        draw.disableScissor();
        gl11.verify(() -> GL11.glScissor(20, 400, 40, 40), times(2));
        gl11.verify(() -> GL11.glDisable(GL11.GL_SCISSOR_TEST), never());
        assertEquals(1, draw.scissorDepth());
        // A box wholly outside the one around it clips everything
        draw.enableScissor(100, 100, 109, 109);
        gl11.verify(() -> GL11.glScissor(200, 400, 0, 0));
        draw.disableScissor();
        draw.disableScissor();
        gl11.verify(() -> GL11.glDisable(GL11.GL_SCISSOR_TEST));
        assertEquals(0, draw.scissorDepth());
        // Deeper than the first stack holds and back out, then an extra disable stays at zero
        for (int i = 0; i < 10; i++) {
            draw.enableScissor(0, 0, 300 - i, 200);
        }
        assertEquals(10, draw.scissorDepth());
        draw.unwindTo(0, 0);
        assertEquals(0, draw.scissorDepth());
        draw.disableScissor();
        assertEquals(0, draw.scissorDepth());
        // Unwinding pops through GL like any other pop
        draw.pushMatrix();
        draw.pushMatrix();
        draw.pushMatrix();
        draw.unwindTo(1, 0);
        assertEquals(1, draw.matrixDepth());
        gl11.verify(GL11::glPopMatrix, times(5));
    }

    @Test
    void modHeadingsAndLogosResolveOncePerMod() throws Exception {
        TextureManager textures = client.getTextureManager();
        when(textures.getDynamicTextureLocation(eq("modlogo"), any(DynamicTexture.class))).thenReturn(new ResourceLocation("dynamic/modlogo_1"));
        Map<String, ModContainer> mods = new HashMap<>();
        mods.put("packed", container("packed", "Packed Mod", "logo.png"));
        mods.put("bundled", container("bundled", "Bundled Mod", "/assets/impetus/textures/gui/impetus.png"));
        mods.put("absent", container("absent", "Absent Logo", "/no/such/logo.png"));
        mods.put("plain", container("plain", "Plain", ""));
        mods.put("broken", container("broken", "Broken", "logo.png"));
        Mixins.set(Loader.instance(), "namedMods", mods);
        IResourcePack packed = mock(IResourcePack.class);
        when(packed.getPackImage()).thenReturn(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
        IResourcePack broken = mock(IResourcePack.class);
        when(broken.getPackImage()).thenThrow(new IOException("unreadable"));
        Map<String, IResourcePack> packs = new HashMap<>();
        packs.put("packed", packed);
        packs.put("broken", broken);
        Mixins.set(FMLClientHandler.instance(), "resourcePackMap", packs);

        VintageDrawContext draw = new VintageDrawContext();
        // A registered mod shows its own name; Impetus's subsystems are capitalised without the default locale
        assertEquals(TextComponent.literal("Packed Mod"), draw.getFriendlyModName("packed"));
        assertEquals(TextComponent.literal("Impetus"), draw.getFriendlyModName("impetus"));
        assertEquals(TextComponent.literal(""), draw.getFriendlyModName(""));
        // Bundled icons first, then the mod's pack image or logo file, and "none" is remembered too
        assertEquals("impetus:textures/gui/fulgor.png", draw.getModLogoPath("fulgor"));
        assertEquals("minecraft:dynamic/modlogo_1", draw.getModLogoPath("packed"));
        assertEquals("minecraft:dynamic/modlogo_1", draw.getModLogoPath("bundled"));
        assertNull(draw.getModLogoPath("absent"));
        assertNull(draw.getModLogoPath("plain"));
        assertNull(draw.getModLogoPath("broken"));
        assertNull(draw.getModLogoPath("unregistered"));
        assertNull(draw.getModLogoPath("unregistered"));
        verify(textures, times(2)).getDynamicTextureLocation(eq("modlogo"), any(DynamicTexture.class));
        Statics.<Map<?, ?>>get(VintageDrawContext.class, "MOD_LOGOS").clear();
    }

    private static ModContainer container(String id, String name, String logo) {
        ModContainer container = mock(ModContainer.class);
        ModMetadata metadata = new ModMetadata();
        metadata.logoFile = logo;
        when(container.getModId()).thenReturn(id);
        when(container.getName()).thenReturn(name);
        when(container.getMetadata()).thenReturn(metadata);
        return container;
    }

    @Test
    void theInteractionContextReadsModifiersAndClicks() {
        keyboard.when(() -> Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)).thenReturn(true);
        assertTrue(VintageInteractionContext.INSTANCE.isSpecialKeyDown(InteractionContext.SpecialKey.SHIFT));
        assertFalse(VintageInteractionContext.INSTANCE.isSpecialKeyDown(InteractionContext.SpecialKey.CTRL));
        assertFalse(VintageInteractionContext.INSTANCE.isSpecialKeyDown(InteractionContext.SpecialKey.ALT));
        SoundHandler sounds = mock(SoundHandler.class);
        when(client.getSoundHandler()).thenReturn(sounds);
        VintageInteractionContext.INSTANCE.playClickSound();
        verify(sounds).playSound(any(ISound.class));
    }

    @Test
    void theBackdropBlursTheCapturedFrame() {
        Statics.set(ScreenBlurBackdrop.class, "texture", -1);
        Statics.set(ScreenBlurBackdrop.class, "textureWidth", 0);
        Statics.set(ScreenBlurBackdrop.class, "textureHeight", 0);
        // The first frame at a size allocates the copy, later ones only refresh it
        ScreenBlurBackdrop.draw(427, 240);
        ScreenBlurBackdrop.draw(427, 240);
        gl11.verify(() -> GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0, 854, 480, 0));
        gl11.verify(() -> GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 854, 480));
        assertNotNull(Mixins.construct(ScreenBlurBackdrop.class));
    }

    @Test
    @SuppressWarnings("deprecation")
    void theVideoOptionsScreenHostsEveryModulesPages() throws IOException {
        GuiScreen parent = mock(GuiScreen.class);
        ImpetusVideoOptionsScreen screen = new ImpetusVideoOptionsScreen(parent);
        screen.setWorldAndResolution(client, 427, 240);
        // Out of a world vanilla's dirt shows behind; in one, the blurred frame
        screen.drawScreen(10, 10, 0.0F);
        client.world = mock(WorldClient.class);
        screen.drawScreen(10, 10, 0.0F);

        // Clicks, drags and releases reach the frame, and a drag only counts after a press
        screen.mouseClicked(100, 60, 0);
        screen.mouseClickMove(104, 66, 0, 5L);
        screen.mouseReleased(104, 66, 0);
        screen.mouseClickMove(110, 70, 0, 9L);
        mouse.when(Mouse::getEventButton).thenReturn(-1);
        mouse.when(Mouse::getEventDWheel).thenReturn(120, -120, 0);
        screen.handleMouseInput();
        screen.handleMouseInput();
        screen.handleMouseInput();
        screen.keyTyped('a', Keyboard.KEY_A);
        screen.keyTyped((char) 0, Keyboard.KEY_ESCAPE);
        verify(client).displayGuiScreen(null);
        screen.initGui();

        ImpetusVideoOptionsController controller = Mixins.get(screen, "controller");
        // Tab nobody takes moves the focus to the first control, the search bar, with Shift read through the vintage context
        screen.keyTyped('\t', Keyboard.KEY_TAB);
        assertInstanceOf(SearchBarWidget.class, controller.getFrame().getFocusedControl());
        Mixins.<Runnable>get(controller, "onClose").run();
        verify(client).displayGuiScreen(parent);

        // Applying reloads what the changed options demand
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;
        TextureMap atlas = mock(TextureMap.class);
        when(client.getTextureMapBlocks()).thenReturn(atlas);
        Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD, OptionFlag.REQUIRES_ASSET_RELOAD));
        verify(renderGlobal).loadRenderers();
        verify(atlas).setMipmapLevels(settings.mipmapLevels);
        verify(client).refreshResources();
        Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_RENDERER_UPDATE));
        verify(renderGlobal).setDisplayListEntitiesDirty();
        client.world = null;
        Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD));
        verify(renderGlobal, times(1)).loadRenderers();
    }

    @Test
    void everyFlagReachesItsSideEffectWhenApplied() {
        ImpetusVideoOptionsScreen screen = new ImpetusVideoOptionsScreen(mock(GuiScreen.class));
        screen.setWorldAndResolution(client, 427, 240);
        ImpetusVideoOptionsController controller = Mixins.get(screen, "controller");
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;
        client.world = mock(WorldClient.class);
        when(client.getTextureMapBlocks()).thenReturn(mock(TextureMap.class));
        try (MockedStatic<Umbra> umbra = Mockito.mockStatic(Umbra.class)) {
            // A pipeline change re-parses the pack, which picks the Nvidium port or the original itself, then rebuilds the renderers around it
            Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD));
            umbra.verify(Umbra::loadCurrentShaderpack);
            umbra.verify(Umbra::reloadIfTerrainBackendChanged, never());
            verify(renderGlobal).loadRenderers();

            // With a renderer reload as well, the pack still loads once and the renderers rebuild once
            Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD, OptionFlag.REQUIRES_RENDERER_RELOAD));
            umbra.verify(Umbra::loadCurrentShaderpack, times(2));
            umbra.verify(Umbra::reloadIfTerrainBackendChanged, never());
            verify(renderGlobal, times(2)).loadRenderers();

            // A rebuild covers a renderer update
            Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD, OptionFlag.REQUIRES_RENDERER_UPDATE));
            verify(renderGlobal, times(3)).loadRenderers();
            verify(renderGlobal, never()).setDisplayListEntitiesDirty();

            // A renderer reload alone only re-picks the pack's copy
            Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD));
            umbra.verify(Umbra::reloadIfTerrainBackendChanged);
            umbra.verify(Umbra::loadCurrentShaderpack, times(3));
            verify(renderGlobal, times(4)).loadRenderers();

            // A restart change reloads nothing and says the game must restart
            Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_GAME_RESTART));
            assertEquals(List.of("Restart required"), ImpetusNotifications.getVisible().stream().map(n -> n.title()).toList());
            umbra.verify(Umbra::loadCurrentShaderpack, times(3));
            umbra.verify(Umbra::reloadIfTerrainBackendChanged, times(1));
            verify(renderGlobal, times(4)).loadRenderers();
            verify(client, never()).refreshResources();

            // Out of a world the pack still reloads, so the next world starts on it, and there are no renderers to rebuild
            client.world = null;
            Statics.callInstance(controller, "applyFlagSideEffects", EnumSet.of(OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD));
            umbra.verify(Umbra::loadCurrentShaderpack, times(4));
            verify(renderGlobal, times(4)).loadRenderers();
        }
    }
}
