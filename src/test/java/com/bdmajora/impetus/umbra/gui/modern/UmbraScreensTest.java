package com.bdmajora.impetus.umbra.gui.modern;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.config.UmbraConfig;
import com.bdmajora.impetus.umbra.gui.PackLanguage;
import com.bdmajora.impetus.umbra.pipeline.ColorSpaceConverter;
import com.bdmajora.impetus.umbra.pipeline.UmbraPipeline;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import com.bdmajora.testing.OptionsScreens;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.GameSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraScreensTest {
    @TempDir
    Path home;

    private Minecraft client;
    private GameSettings settings;
    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;

    private static final String DEMO_SOURCE = String.join("\n",
            "#version 120",
            "#define BLOOM // Bloom toggle",
            "//#define SUN_GLOW",
            "#define QUALITY 2 // Quality [1 2 3]",
            "#ifdef BLOOM",
            "#endif",
            "#ifdef SUN_GLOW",
            "#endif",
            "void main() {}");

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void freshGame() throws IOException {
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        settings.language = "de_DE";
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 800;
        client.displayHeight = 600;
        OptionsScreens.prepare(client, home);
        when(client.getSoundHandler()).thenReturn(mock(SoundHandler.class));
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        writePack("Demo", Map.of(
                "shaders.properties", String.join("\n",
                        "shadowDistance = 128",
                        "profile.LOW = !BLOOM QUALITY=1",
                        "profile.HIGH = BLOOM QUALITY=3",
                        "screen = <profile> <empty> QUALITY BLOOM [LIGHTING] MISSING",
                        "screen.columns = 3",
                        "screen.LIGHTING = SUN_GLOW",
                        "screen.LIGHTING.columns = 1"),
                "final.fsh", DEMO_SOURCE,
                "lang/en_US.lang", String.join("\n",
                        "# a comment",
                        "option.QUALITY=&aQuality",
                        "option.QUALITY.comment=How good it looks. Costs frames",
                        "value.QUALITY.3=Ultra",
                        "prefix.QUALITY=[",
                        "suffix.QUALITY=]",
                        "screen.LIGHTING=Lighting & Sky",
                        "screen.profile=Preset",
                        "profile.HIGH=High",
                        "not a key",
                        "=no key"),
                "lang/de_de.lang", "option.BLOOM=Leuchten\nvalue.BLOOM.true=Glühend"));
        writePack("Bare", Map.of(
                "shaders.properties", "profile.ONLY = QUALITY=2",
                "final.fsh", DEMO_SOURCE));
    }

    @AfterEach
    void forgetGame() {
        gl13.close();
        gl11.close();
        OptionsScreens.forget();
        Mixins.set(Umbra.class, "config", null);
        Mixins.set(Umbra.class, "currentPack", null);
        Mixins.set(Umbra.class, "renderingPipeline", null);
        Mixins.set(Umbra.class, "pipeline", null);
        Mixins.set(Umbra.class, "pipelineNeedsInit", false);
        Mixins.set(Umbra.class, "renderingPipelineFailed", false);
        Mixins.set(Umbra.class, "resetShaderPackOptions", false);
        Statics.<Map<?, ?>>get(Umbra.class, "shaderPackOptionQueue").clear();
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.SRGB);
        client.world = null;
    }

    private void writePack(String name, Map<String, String> files) throws IOException {
        Path shaders = home.resolve("shaderpacks").resolve(name).resolve("shaders");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = shaders.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
    }

    private static List<?> widgets(GuiScreen screen) {
        return Mixins.get(screen, "widgets");
    }

    private static String labelOf(Object widget) {
        TextComponent label = widget instanceof OptionButtonWidget ? Mixins.get(widget, "label") : ((FlatButtonWidget) widget).getLabel();
        return ((TextComponent.Literal) label).text();
    }

    private static Dim2i dimOf(Object widget) {
        return widget instanceof OptionButtonWidget option ? option.getDim() : Mixins.get(widget, "dim");
    }

    private static Object widget(GuiScreen screen, String label) {
        return widgets(screen).stream().filter(w -> labelOf(w).equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError(label + " among " + widgets(screen).stream().map(UmbraScreensTest::labelOf).toList()));
    }

    private static boolean has(GuiScreen screen, String label) {
        return widgets(screen).stream().anyMatch(w -> labelOf(w).equals(label));
    }

    private static void click(GuiScreen screen, String label, int button) {
        Dim2i dim = dimOf(widget(screen, label));
        if (screen instanceof ShaderPackSelectScreen select) {
            select.mouseClicked(dim.getCenterX(), dim.getCenterY(), button);
        } else {
            ((ShaderPackConfigScreen) screen).mouseClicked(dim.getCenterX(), dim.getCenterY(), button);
        }
    }

    @Test
    void packLanguagesLayerTheActiveLocaleOverEnglish() {
        Map<AbsolutePackPath, String> sources = Map.of(
                AbsolutePackPath.fromAbsolutePath("/lang/EN_us.lang"), "option.A=&cRed & Blue\nvalue.A.1=One\n",
                AbsolutePackPath.fromAbsolutePath("/lang/fr_fr.lang"), "option.A=Rouge &z\r\nprofile.P=Profil");
        PackLanguage english = new PackLanguage(sources, null);
        assertEquals("§cRed & Blue", english.optionLabel("A", "fallback"));
        assertEquals("fallback", english.optionLabel("B", "fallback"));
        assertEquals("B", english.optionLabel("B", null));
        assertEquals("One", english.valueLabel("A", "1"));
        assertEquals("2", english.valueLabel("A", "2"));
        assertEquals("", english.prefix("A"));
        assertEquals("", english.suffix("A"));
        assertEquals("S", english.screenLabel("S"));
        assertNull(english.comment("A"));
        assertEquals("P", english.profileLabel("P"));
        PackLanguage french = new PackLanguage(sources, "FR_FR");
        assertEquals("Rouge &z", french.optionLabel("A", null));
        assertEquals("One", french.valueLabel("A", "1"));
        assertEquals("Profil", french.profileLabel("P"));
        assertEquals("x", new PackLanguage(Map.of(), "en_us").optionLabel("x", null));
    }

    @Test
    void theConfigRemembersTheSelectedPackAndColourSpace() throws IOException {
        UmbraConfig config = new UmbraConfig(home.resolve("other"));
        assertEquals(home.resolve("other/shaderpacks"), config.getShaderpacksDirectory());
        assertTrue(config.listShaderpacks().isEmpty());
        config.load();
        assertEquals(UmbraConfig.NO_PACK, config.getShaderPackName());
        assertFalse(config.isShaderPackEnabled());
        assertNull(config.getSelectedPackPath());
        config.setShaderPackName("  ");
        assertEquals(UmbraConfig.NO_PACK, config.getShaderPackName());
        config.setShaderPackName(null);
        config.setShaderPackName(" Demo ");
        assertEquals(home.resolve("other/shaderpacks/Demo"), config.getSelectedPackPath());
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.DCI_P3);
        config.save();
        config.ensureShaderpacksDirectory();
        Files.createDirectories(home.resolve("other/shaderpacks/Folder"));
        Files.writeString(home.resolve("other/shaderpacks/archive.ZIP"), "");
        Files.writeString(home.resolve("other/shaderpacks/notes.txt"), "");
        assertEquals(List.of("archive.ZIP", "Folder"), config.listShaderpacks());
        ColorSpaceConverter.setColorSpace(ColorSpaceConverter.ColorSpace.SRGB);
        UmbraConfig reread = new UmbraConfig(home.resolve("other"));
        reread.load();
        assertEquals("Demo", reread.getShaderPackName());
        assertEquals(ColorSpaceConverter.ColorSpace.DCI_P3, ColorSpaceConverter.getColorSpace());
    }

    @Test
    void umbraLoadsReloadsAndPersistsThePacksOptions() throws IOException {
        // No selection yet: nothing loads
        Umbra.initialize(home);
        assertNotNull(Umbra.getConfig());
        assertNotNull(Umbra.logger());
        assertEquals(UmbraConfig.NO_PACK, Umbra.getSelectedPackName());
        assertNull(Umbra.getCurrentPack());
        assertEquals(List.of("Bare", "Demo"), Umbra.listAvailablePacks());

        // Selecting a pack persists it and parses it; option changes are written to the pack's own .txt
        Umbra.setShaderpackAndReload("Demo");
        assertEquals("Demo", Umbra.getSelectedPackName());
        assertNotNull(Umbra.getCurrentPack());
        assertTrue(Umbra.isShaderPackInUse());
        Umbra.queueShaderPackOptions(Map.of("QUALITY", "3", "BLOOM", "false"));
        Properties saved = new Properties();
        try (var in = Files.newInputStream(home.resolve("shaderpacks/Demo.txt"))) {
            saved.load(in);
        }
        assertEquals("3", saved.getProperty("QUALITY"));
        assertEquals("false", saved.getProperty("BLOOM"));
        assertEquals("3", Umbra.getCurrentPack().getShaderPackOptions().getOptionValues().getStringValue("QUALITY").orElseThrow());
        // Reset drops every change
        Umbra.resetShaderPackOptionsAndReload();
        assertFalse(Umbra.getCurrentPack().getShaderPackOptions().getOptionValues().getStringValue("QUALITY").isPresent());
        // A selection that persists across a restart loads straight away
        Mixins.set(Umbra.class, "config", null);
        Umbra.initialize(home);
        assertNotNull(Umbra.getCurrentPack());

        // A pack that is gone, or that cannot be read, leaves shaders off
        Umbra.setShaderpackAndReload("Gone");
        assertNull(Umbra.getCurrentPack());
        Files.writeString(home.resolve("shaderpacks/Broken.zip"), "not a zip");
        Umbra.setShaderpackAndReload("Broken.zip");
        assertNull(Umbra.getCurrentPack());
        // An unreadable options file loads defaults
        Files.createDirectories(home.resolve("shaderpacks/Bare.txt"));
        Umbra.setShaderpackAndReload("Bare");
        assertNotNull(Umbra.getCurrentPack());

        // The render thread tears pipelines down once a pack goes away, and leaves them for the next frame while one is active
        UmbraRenderingPipeline rendering = mock(UmbraRenderingPipeline.class);
        UmbraPipeline compiled = mock(UmbraPipeline.class);
        Mixins.set(Umbra.class, "renderingPipeline", rendering);
        Mixins.set(Umbra.class, "pipeline", compiled);
        assertSame(compiled, Umbra.getPipeline());
        Umbra.updatePipeline();
        verify(rendering, Mockito.never()).destroy();
        Umbra.unloadShaderpack();
        Umbra.updatePipeline();
        Umbra.updatePipeline();
        verify(rendering).destroy();
        verify(compiled).destroy();
        assertNull(Umbra.getPipeline());

        // Without a config there is no selection to change, and an unreadable config leaves shaders off
        Mixins.set(Umbra.class, "config", null);
        Umbra.setShaderpackAndReload("Demo");
        assertTrue(Umbra.listAvailablePacks().isEmpty());
        Path broken = home.resolve("broken");
        Files.createDirectories(broken.resolve("optionsshaders.txt"));
        Umbra.initialize(broken);
        assertNull(Umbra.getCurrentPack());
    }

    @Test
    void theVideoOptionsPageOpensThePickerAndReportsThePack() throws Exception {
        GuiScreen parent = mock(GuiScreen.class);
        OptionPage page = UmbraOptionPages.shaderPacks(parent);
        assertEquals(3, OptionPages.exercise(List.of(page)));
        // Picking a colour space applies it and saves when there is a config
        @SuppressWarnings("unchecked")
        Option<ColorSpaceConverter.ColorSpace> colorSpace = (Option<ColorSpaceConverter.ColorSpace>) page.getOptions().get(1);
        colorSpace.setValue(ColorSpaceConverter.ColorSpace.REC2020);
        colorSpace.applyChanges();
        assertEquals(ColorSpaceConverter.ColorSpace.REC2020, ColorSpaceConverter.getColorSpace());
        Umbra.initialize(home);
        colorSpace.setValue(ColorSpaceConverter.ColorSpace.ADOBE_RGB);
        colorSpace.applyChanges();
        assertTrue(Files.readString(home.resolve("optionsshaders.txt")).contains("ADOBE_RGB"));
        // The shadow distance row reports the loaded pack's own value
        Option<?> shadowDistance = page.getOptions().get(2);
        assertEquals("Default", shadowDistance.getValue());
        Umbra.setShaderpackAndReload("Bare");
        assertEquals("Default", shadowDistance.getValue());
        Umbra.setShaderpackAndReload("Demo");
        assertEquals("128 blocks", shadowDistance.getValue());
        Runnable open = Mixins.get(page.getOptions().get(0).getControl(), "action");
        open.run();
        verify(client).displayGuiScreen(any(ShaderPackSelectScreen.class));
        assertEquals("sRGB", Mixins.call(Statics.get(UmbraOptionPages.class, "STATE"), "colorSpace"));
        assertNotNull(Mixins.construct(UmbraOptionPages.class));
    }

    @Test
    void thePickerStagesASelectionUntilApplied() throws IOException {
        for (int i = 0; i < 8; i++) {
            Files.createDirectories(home.resolve("shaderpacks/Extra" + i));
        }
        Umbra.initialize(home);
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;
        GuiScreen parent = mock(GuiScreen.class);
        ShaderPackSelectScreen screen = new ShaderPackSelectScreen(parent);
        screen.setWorldAndResolution(client, 480, 300);
        assertTrue(has(screen, "Shaders: §cDisabled"));
        // Ten packs over two pages
        assertTrue(has(screen, "Bare"));
        assertFalse(has(screen, "Extra7"));
        click(screen, "Next >", 0);
        assertTrue(has(screen, "Extra7"));
        click(screen, "< Prev", 0);
        // Choosing a pack only stages it; Apply loads it and rebuilds the chunk renderers
        click(screen, "Demo", 0);
        assertTrue(has(screen, "Shaders: §aEnabled"));
        assertTrue(has(screen, "§eDemo"));
        assertEquals(UmbraConfig.NO_PACK, Umbra.getSelectedPackName());
        click(screen, "Apply", 0);
        assertEquals("Demo", Umbra.getSelectedPackName());
        verify(renderGlobal).loadRenderers();
        // Applying the same selection again does nothing
        click(screen, "Apply", 0);
        verify(renderGlobal, times(1)).loadRenderers();
        // The settings screen is reachable once a pack is in use
        click(screen, "Shader Pack Settings...", 0);
        verify(client).displayGuiScreen(any(ShaderPackConfigScreen.class));
        // Turning shaders off and finishing applies it too
        click(screen, "Shaders: §aEnabled", 0);
        click(screen, "Done", 0);
        assertEquals(UmbraConfig.NO_PACK, Umbra.getSelectedPackName());
        verify(client).displayGuiScreen(parent);
        click(screen, "Cancel", 0);
        verify(client, times(2)).displayGuiScreen(parent);
        // A click on nothing, a draw out of and in a world, and the folder button with no config to open
        screen.mouseClicked(0, 0, 0);
        screen.drawScreen(0, 0, 0.0F);
        client.world = mock(WorldClient.class);
        screen.drawScreen(0, 0, 0.0F);
        Mixins.set(Umbra.class, "config", null);
        click(screen, "Open Shader Pack Folder...", 0);
    }

    @Test
    void theConfigScreenFollowsThePacksLayout() {
        GuiScreen parent = mock(GuiScreen.class);
        // With no pack there is only a way back
        ShaderPackConfigScreen empty = new ShaderPackConfigScreen(parent);
        empty.setWorldAndResolution(client, 480, 300);
        empty.drawScreen(0, 0, 0.0F);
        click(empty, "Done", 0);
        verify(client).displayGuiScreen(parent);

        Umbra.initialize(home);
        Umbra.setShaderpackAndReload("Demo");
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;
        ShaderPackConfigScreen screen = new ShaderPackConfigScreen(parent);
        screen.setWorldAndResolution(client, 480, 300);
        // The pack's own layout and lang: a profile tile, labelled options, a sub-screen link and a tile for an undeclared name
        assertTrue(has(screen, "Preset"));
        assertTrue(has(screen, "§aQuality"));
        assertTrue(has(screen, "Leuchten"));
        assertTrue(has(screen, "Lighting & Sky"));
        assertTrue(has(screen, "§7MISSING"));
        assertEquals("[2]", Mixins.get(widget(screen, "§aQuality"), "value"));
        assertEquals("Glühend", Mixins.get(widget(screen, "Leuchten"), "value"));
        assertFalse((boolean) Mixins.get(widget(screen, "Apply"), "enabled"));
        // Options cycle both ways through their values and booleans flip; nothing applies until asked
        click(screen, "§aQuality", 0);
        assertEquals("[Ultra]", Mixins.get(widget(screen, "§aQuality"), "value"));
        click(screen, "§aQuality", 0);
        click(screen, "§aQuality", 1);
        click(screen, "§aQuality", 1);
        assertEquals("[2]", Mixins.get(widget(screen, "§aQuality"), "value"));
        click(screen, "Leuchten", 0);
        assertEquals("§cOFF", Mixins.get(widget(screen, "Leuchten"), "value"));
        Map<String, String> pending = Mixins.get(screen, "pending");
        assertEquals(Map.of("QUALITY", "2", "BLOOM", "false"), pending);
        // Profiles step forward and back through the pack's presets
        click(screen, "Preset", 0);
        assertEquals("LOW", Mixins.get(widget(screen, "Preset"), "value"));
        click(screen, "Preset", 1);
        assertEquals("High", Mixins.get(widget(screen, "Preset"), "value"));
        // The undeclared name does nothing
        click(screen, "§7MISSING", 0);
        // Hovering an option with a comment shows it
        Dim2i quality = dimOf(widget(screen, "§aQuality"));
        screen.drawScreen(quality.getCenterX(), quality.getCenterY(), 0.0F);
        // The sub-screen has its own layout and a way back
        click(screen, "Lighting & Sky", 0);
        assertTrue(has(screen, "SUN_GLOW"));
        screen.drawScreen(0, 0, 0.0F);
        click(screen, "< Back", 0);
        assertTrue(has(screen, "Preset"));
        // Apply queues the changes and rebuilds the chunk renderers; Reset All drops them
        click(screen, "Apply", 0);
        assertTrue(pending.isEmpty());
        assertEquals("3", Umbra.getCurrentPack().getShaderPackOptions().getOptionValues().getStringValue("QUALITY").orElseThrow());
        verify(renderGlobal).loadRenderers();
        click(screen, "Reset All", 0);
        verify(renderGlobal, times(2)).loadRenderers();
        click(screen, "§aQuality", 0);
        click(screen, "Done", 0);
        verify(renderGlobal, times(3)).loadRenderers();
        verify(client, times(2)).displayGuiScreen(parent);
        click(screen, "Done", 0);
        screen.mouseClicked(0, 0, 0);

        // A short window pages the tiles
        screen.setWorldAndResolution(client, 480, 120);
        click(screen, "Next >", 0);
        click(screen, "< Prev", 0);
        client.world = mock(WorldClient.class);
        screen.drawScreen(0, 0, 0.0F);

        // A pack with no layout gets one generated from its options, profiles first
        Umbra.setShaderpackAndReload("Bare");
        ShaderPackConfigScreen generated = new ShaderPackConfigScreen(parent);
        generated.setWorldAndResolution(client, 480, 300);
        assertTrue(has(generated, "profile"));
        assertTrue(has(generated, "Quality"));
        assertTrue(has(generated, "Bloom toggle"));
        assertTrue(has(generated, "SUN_GLOW"));
        assertEquals("§aON", Mixins.get(widget(generated, "Bloom toggle"), "value"));
        assertEquals("ONLY", Mixins.get(widget(generated, "profile"), "value"));
        click(generated, "Quality", 0);
        assertEquals("§7Custom", Mixins.get(widget(generated, "profile"), "value"));
        OptionButtonWidget tile = (OptionButtonWidget) widget(generated, "Quality");
        assertTrue(tile.isMouseOver(tile.getDim().getCenterX(), tile.getDim().getCenterY()));
        assertFalse(tile.isMouseOver(-1, -1));
    }
}
