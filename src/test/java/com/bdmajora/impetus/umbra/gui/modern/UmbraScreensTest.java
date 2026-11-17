package com.bdmajora.impetus.umbra.gui.modern;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
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
import com.bdmajora.impetus.umbra.shaderpack.loading.ProgramId;
import com.bdmajora.impetus.umbra.terrain.UmbraMeshShading;
import com.bdmajora.impetus.umbra.terrain.mesh.NvidiumPackPorter;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UmbraScreensTest {
    @TempDir
    Path home;

    private Minecraft client;
    private GameSettings settings;
    private ImpetusGameOptions options;
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
        options = OptionsScreens.prepare(client, home);
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
                "lang/de_de.lang", "option.BLOOM=Leuchten\nvalue.BLOOM.true=Glühend",
                "gbuffers_terrain.vsh", "#version 120\nvarying vec2 uv;\nvoid main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.xy; }",
                "gbuffers_terrain.fsh", "#version 120\nvarying vec2 uv;\nuniform sampler2D texture;\nvoid main() { gl_FragData[0] = texture2D(texture, uv); }"));
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
        Mixins.set(Umbra.class, "loadedForNvidium", false);
        Mixins.set(Umbra.class, "nvidiumPortNote", "");
        Statics.set(MeshShaderSupport.class, "supported", null);
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
    void theConfigKeepsTheLastPackWhileShadersAreOff() throws IOException {
        UmbraConfig config = new UmbraConfig(home);
        config.load();
        assertEquals(UmbraConfig.NO_PACK, config.getLastShaderPackName());
        // Checks the switch with no pack picked.
        assertFalse(config.setShadersEnabled(true));
        assertFalse(config.setShadersEnabled(false));
        config.rememberShaderPackName(null);
        config.rememberShaderPackName("  ");
        config.rememberShaderPackName(UmbraConfig.NO_PACK);
        assertEquals(UmbraConfig.NO_PACK, config.getLastShaderPackName());

        // Checks the pack is kept across off and on.
        config.setShaderPackName(" Demo ");
        assertEquals("Demo", config.getLastShaderPackName());
        assertTrue(config.setShadersEnabled(false));
        assertFalse(config.isShaderPackEnabled());
        assertEquals(UmbraConfig.NO_PACK, config.getShaderPackName());
        assertEquals("Demo", config.getLastShaderPackName());
        config.setShaderPackName(UmbraConfig.NO_PACK);
        assertEquals("Demo", config.getLastShaderPackName());

        // Both lines survive a restart
        config.save();
        Properties written = new Properties();
        try (var reader = Files.newBufferedReader(home.resolve("optionsshaders.txt"))) {
            written.load(reader);
        }
        assertEquals(UmbraConfig.NO_PACK, written.getProperty("shaderPack"));
        assertEquals("Demo", written.getProperty("lastShaderPack"));
        UmbraConfig reread = new UmbraConfig(home);
        reread.load();
        assertFalse(reread.isShaderPackEnabled());
        assertEquals("Demo", reread.getLastShaderPackName());
        assertTrue(reread.setShadersEnabled(true));
        assertEquals("Demo", reread.getShaderPackName());
        assertFalse(reread.setShadersEnabled(true));

        // Checks the last pack line.
        Files.writeString(home.resolve("optionsshaders.txt"), "shaderPack=Bare\nlastShaderPack=Demo\n");
        UmbraConfig active = new UmbraConfig(home);
        active.load();
        assertEquals("Bare", active.getLastShaderPackName());
        Files.writeString(home.resolve("optionsshaders.txt"), "shaderPack=(internal)\n");
        UmbraConfig older = new UmbraConfig(home);
        older.load();
        assertEquals(UmbraConfig.NO_PACK, older.getLastShaderPackName());
        assertFalse(older.setShadersEnabled(true));

        // Installed means a folder or zip of that name is in shaderpacks
        assertTrue(config.isPackInstalled("Demo"));
        assertTrue(config.isPackInstalled(" Bare "));
        assertFalse(config.isPackInstalled("Gone"));
        assertFalse(config.isPackInstalled(UmbraConfig.NO_PACK));
        assertFalse(config.isPackInstalled(null));
    }

    @Test
    void togglingShadersKeepsThePackAcrossOffAndBackOn() throws IOException {
        // Checks the toggle without a config.
        assertEquals(Umbra.ToggleResult.NO_PACK, Umbra.toggleShaders());
        assertEquals(Umbra.ToggleResult.NO_PACK, Umbra.storeShadersEnabled(false));
        assertFalse(Umbra.selectShaderpack("Demo", true));
        assertFalse(Umbra.areShadersEnabled());
        assertFalse(Umbra.canEnableShaders());
        assertEquals(UmbraConfig.NO_PACK, Umbra.getLastPackName());

        Umbra.initialize(home);
        assertEquals(Umbra.ToggleResult.NO_PACK, Umbra.toggleShaders());
        assertFalse(Umbra.canEnableShaders());

        // Checks off keeps the pack across a restart.
        Umbra.setShaderpackAndReload("Demo");
        assertTrue(Umbra.areShadersEnabled());
        assertEquals(Umbra.ToggleResult.DISABLED, Umbra.toggleShaders());
        assertNull(Umbra.getCurrentPack());
        assertEquals(UmbraConfig.NO_PACK, Umbra.getSelectedPackName());
        assertEquals("Demo", Umbra.getLastPackName());
        assertTrue(Umbra.canEnableShaders());
        Umbra.initialize(home);
        assertEquals("Demo", Umbra.getLastPackName());
        assertEquals(Umbra.ToggleResult.ENABLED, Umbra.toggleShaders());
        assertEquals("Demo", Umbra.getSelectedPackName());
        assertNotNull(Umbra.getCurrentPack());

        // Asking for the state already in place reloads nothing
        Object loaded = Umbra.getCurrentPack();
        assertEquals(Umbra.ToggleResult.ENABLED, Umbra.setShadersEnabled(true));
        assertSame(loaded, Umbra.getCurrentPack());
        assertEquals(Umbra.ToggleResult.DISABLED, Umbra.setShadersEnabled(false));
        assertEquals(Umbra.ToggleResult.DISABLED, Umbra.setShadersEnabled(false));

        // A remembered pack that was deleted stays off
        assertFalse(Umbra.selectShaderpack("Gone", false));
        assertEquals("Gone", Umbra.getLastPackName());
        assertFalse(Umbra.canEnableShaders());
        assertEquals(Umbra.ToggleResult.MISSING, Umbra.toggleShaders());
        assertFalse(Umbra.areShadersEnabled());

        // Checks a pack that will not load.
        Files.writeString(home.resolve("shaderpacks/Broken.zip"), "not a zip");
        assertFalse(Umbra.selectShaderpack("Broken.zip", false));
        assertEquals(Umbra.ToggleResult.FAILED, Umbra.toggleShaders());
        assertFalse(Umbra.areShadersEnabled());
        assertNull(Umbra.getCurrentPack());
        assertEquals("Broken.zip", Umbra.getLastPackName());
        assertTrue(Files.readString(home.resolve("optionsshaders.txt")).contains("shaderPack=(internal)"));

        // Checks storing without loading.
        assertFalse(Umbra.selectShaderpack("Bare", false));
        assertEquals(Umbra.ToggleResult.ENABLED, Umbra.storeShadersEnabled(true));
        assertTrue(Umbra.areShadersEnabled());
        assertNull(Umbra.getCurrentPack());
        assertEquals(Umbra.ToggleResult.DISABLED, Umbra.storeShadersEnabled(false));
        assertEquals(Umbra.ToggleResult.DISABLED, Umbra.storeShadersEnabled(false));
    }

    @Test
    void theShadersSwitchOnTheVideoPageKeepsThePackAcrossOff() throws IOException {
        OptionPage page = UmbraOptionPages.shaderPacks(mock(GuiScreen.class));
        @SuppressWarnings("unchecked")
        Option<Boolean> shaders = (Option<Boolean>) page.getOptions().get(0);
        // Applying it reloads the pack and the chunks through the video screen's pipeline flag
        assertEquals(List.of(com.bdmajora.impetus.api.options.structure.OptionFlag.REQUIRES_SHADER_PIPELINE_RELOAD), List.copyOf(shaders.getFlags()));
        // Greyed out without a config and until a pack was picked once
        assertFalse(shaders.isAvailable());
        assertFalse(shaders.getValue());
        Umbra.initialize(home);
        assertFalse(shaders.isAvailable());
        Umbra.setShaderpackAndReload("Demo");
        assertTrue(shaders.isAvailable());
        assertTrue(shaders.getValue());

        // Checks off stays available.
        shaders.setValue(false);
        shaders.applyChanges();
        assertFalse(Umbra.areShadersEnabled());
        assertFalse(shaders.getValue());
        assertTrue(shaders.isAvailable());
        assertTrue(Files.readString(home.resolve("optionsshaders.txt")).contains("lastShaderPack=Demo"));
        shaders.setValue(true);
        shaders.applyChanges();
        assertEquals("Demo", Umbra.getSelectedPackName());
        assertTrue(shaders.getValue());
    }

    @Test
    void thePickerStartsFromTheKeptPackWhenShadersAreOff() throws IOException {
        Umbra.initialize(home);
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;
        Umbra.setShaderpackAndReload("Demo");
        Umbra.toggleShaders();
        ShaderPackSelectScreen screen = new ShaderPackSelectScreen(mock(GuiScreen.class));
        screen.setWorldAndResolution(client, 480, 300);
        assertTrue(has(screen, "Shaders: §cDisabled"));
        assertTrue(has(screen, "§7Demo §8(off)"));

        // The switch row alone brings the kept pack back
        click(screen, "Shaders: §cDisabled", 0);
        assertTrue(has(screen, "§eDemo"));
        click(screen, "Apply", 0);
        assertEquals("Demo", Umbra.getSelectedPackName());
        verify(renderGlobal).loadRenderers();

        // Another pick switched off on the way is what comes back next time
        click(screen, "Bare", 0);
        click(screen, "Shaders: §aEnabled", 0);
        assertTrue(has(screen, "§7Bare §8(off)"));
        click(screen, "Apply", 0);
        assertEquals(UmbraConfig.NO_PACK, Umbra.getSelectedPackName());
        assertEquals("Bare", Umbra.getLastPackName());
        verify(renderGlobal, times(2)).loadRenderers();

        // Changing only the kept pack saves it without rebuilding the chunks
        click(screen, "Demo", 0);
        click(screen, "Shaders: §aEnabled", 0);
        click(screen, "Apply", 0);
        assertEquals("Demo", Umbra.getLastPackName());
        assertTrue(Files.readString(home.resolve("optionsshaders.txt")).contains("lastShaderPack=Demo"));
        verify(renderGlobal, times(2)).loadRenderers();
    }

    @Test
    void underNvidiumThePackLoadsFromAPortMadeAndRefreshedOnItsOwn() throws IOException {
        UmbraConfig config = new UmbraConfig(home.resolve("other"));
        assertEquals("Pack", UmbraConfig.portName("Pack.Zip"));
        assertEquals("Pack", UmbraConfig.portName("Pack"));
        assertEquals(home.resolve("other/nvidium_shaderpacks/Pack"), config.portPath("Pack.zip"));

        options.graphicsApi = ImpetusGameOptions.GraphicsApi.NVIDIUM;
        Statics.set(MeshShaderSupport.class, "supported", true);
        // Checks the reload without a config or pack.
        assertFalse(Umbra.reloadIfTerrainBackendChanged());
        Umbra.initialize(home);
        assertFalse(Umbra.reloadIfTerrainBackendChanged());

        // Checks selecting a pack writes its port.
        Files.writeString(home.resolve("shaderpacks/Demo.txt"), "QUALITY=3\n");
        Umbra.setShaderpackAndReload("Demo");
        Path port = home.resolve("nvidium_shaderpacks/Demo");
        assertTrue(Files.isRegularFile(port.resolve("shaders/gbuffers_terrain.mesh")));
        assertTrue(Files.isRegularFile(port.resolve(NvidiumPackPorter.MANIFEST)));
        assertTrue(Umbra.getCurrentPack().getProgramSet().get(ProgramId.TerrainCutoutMip).orElseThrow().hasMeshStages());
        assertTrue(UmbraMeshShading.canRun(Umbra.getCurrentPack()));
        assertEquals("", Umbra.getNvidiumPortNote());
        assertEquals("3", Umbra.getCurrentPack().getShaderPackOptions().getOptionValues().getStringValue("QUALITY").orElseThrow());
        // Checks option changes go to the pack's file.
        Umbra.queueShaderPackOptions(Map.of("QUALITY", "1"));
        assertTrue(Files.readString(home.resolve("shaderpacks/Demo.txt")).contains("QUALITY=1"));
        assertFalse(Files.exists(home.resolve("nvidium_shaderpacks/Demo.txt")));
        assertFalse(Umbra.reloadIfTerrainBackendChanged());

        // Checks the port reuse.
        Files.writeString(port.resolve("marker.txt"), "x");
        Umbra.loadCurrentShaderpack();
        assertTrue(Files.exists(port.resolve("marker.txt")));
        Files.writeString(home.resolve("shaderpacks/Demo/shaders/gbuffers_terrain.gsh"), "#version 150\nvoid main() {}");
        Umbra.loadCurrentShaderpack();
        assertFalse(Files.exists(port.resolve("marker.txt")));
        // Checks a pack whose terrain cannot be ported.
        assertEquals("gbuffers_terrain: geometry or tessellation stage", Umbra.getNvidiumPortNote());
        assertFalse(UmbraMeshShading.canRun(Umbra.getCurrentPack()));
        assertEquals("gbuffers_terrain: geometry or tessellation stage", UmbraMeshShading.whyNot(Umbra.getCurrentPack()));

        // Checks a foreign folder in the way.
        Files.writeString(Files.createDirectories(home.resolve("nvidium_shaderpacks/Bare")).resolve("mine.txt"), "keep");
        Umbra.setShaderpackAndReload("Bare");
        assertNotNull(Umbra.getCurrentPack());
        assertTrue(Umbra.getNvidiumPortNote().startsWith("could not port the pack: Bare already exists"));
        assertEquals("keep", Files.readString(home.resolve("nvidium_shaderpacks/Bare/mine.txt")));
        // A pack that is gone has nothing to port and does not load
        Umbra.setShaderpackAndReload("Gone");
        assertNull(Umbra.getCurrentPack());

        // Checks the original loads back on OpenGL.
        Umbra.setShaderpackAndReload("Demo");
        options.graphicsApi = ImpetusGameOptions.GraphicsApi.OPENGL;
        assertTrue(Umbra.reloadIfTerrainBackendChanged());
        assertFalse(Umbra.getCurrentPack().getProgramSet().get(ProgramId.TerrainCutoutMip).orElseThrow().hasMeshStages());
        assertFalse(Umbra.reloadIfTerrainBackendChanged());
    }

    @Test
    void umbraLoadsReloadsAndPersistsThePacksOptions() throws IOException {
        // Checks nothing loads without a selection.
        Umbra.initialize(home);
        assertNotNull(Umbra.getConfig());
        assertNotNull(Umbra.logger());
        assertEquals(UmbraConfig.NO_PACK, Umbra.getSelectedPackName());
        assertNull(Umbra.getCurrentPack());
        assertEquals(List.of("Bare", "Demo"), Umbra.listAvailablePacks());

        // Checks selecting a pack.
        Umbra.onWorldChanged(-1, "the_nether");
        Umbra.setShaderpackAndReload("Demo");
        Umbra.onWorldChanged(0, null);
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

        // Checks a missing or unreadable pack.
        Umbra.setShaderpackAndReload("Gone");
        assertNull(Umbra.getCurrentPack());
        Files.writeString(home.resolve("shaderpacks/Broken.zip"), "not a zip");
        Umbra.setShaderpackAndReload("Broken.zip");
        assertNull(Umbra.getCurrentPack());
        // An unreadable options file loads defaults
        Files.createDirectories(home.resolve("shaderpacks/Bare.txt"));
        Umbra.setShaderpackAndReload("Bare");
        assertNotNull(Umbra.getCurrentPack());

        // Checks the pipeline teardown.
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

        // Checks a missing and an unreadable config.
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
        assertEquals(4, OptionPages.exercise(List.of(page)));
        // Picking a colour space applies it and saves when there is a config
        @SuppressWarnings("unchecked")
        Option<ColorSpaceConverter.ColorSpace> colorSpace = (Option<ColorSpaceConverter.ColorSpace>) page.getOptions().get(2);
        colorSpace.setValue(ColorSpaceConverter.ColorSpace.REC2020);
        colorSpace.applyChanges();
        assertEquals(ColorSpaceConverter.ColorSpace.REC2020, ColorSpaceConverter.getColorSpace());
        Umbra.initialize(home);
        colorSpace.setValue(ColorSpaceConverter.ColorSpace.ADOBE_RGB);
        colorSpace.applyChanges();
        assertTrue(Files.readString(home.resolve("optionsshaders.txt")).contains("ADOBE_RGB"));
        // The shadow distance row reports the loaded pack's own value
        Option<?> shadowDistance = page.getOptions().get(3);
        assertEquals("Default", shadowDistance.getValue());
        Umbra.setShaderpackAndReload("Bare");
        assertEquals("Default", shadowDistance.getValue());
        Umbra.setShaderpackAndReload("Demo");
        Object label = shadowDistance.getValue();
        assertEquals("128 blocks", label);
        // Checks the label is made once per pack.
        assertSame(label, shadowDistance.getValue());
        // Another pack and then the first again each get their own label
        Umbra.setShaderpackAndReload("Bare");
        assertEquals("Default", shadowDistance.getValue());
        Umbra.setShaderpackAndReload("Demo");
        assertEquals("128 blocks", shadowDistance.getValue());
        assertSame(shadowDistance.getValue(), shadowDistance.getValue());
        Runnable open = Mixins.get(page.getOptions().get(1).getControl(), "action");
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
        // Checks a pick only stages the pack.
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
        // Checks clicks and draws and the folder button.
        screen.mouseClicked(0, 0, 0);
        screen.drawScreen(0, 0, 0.0F);
        client.world = mock(WorldClient.class);
        screen.drawScreen(0, 0, 0.0F);
        Mixins.set(Umbra.class, "config", null);
        click(screen, "Open Shader Pack Folder...", 0);
    }

    // Every string the screen's widgets handed the font in one frame
    private List<String> frameOf(GuiScreen screen) {
        Mockito.clearInvocations(client.fontRenderer);
        screen.drawScreen(0, 0, 0.0F);
        ArgumentCaptor<String> drawn = ArgumentCaptor.forClass(String.class);
        verify(client.fontRenderer, Mockito.atLeast(1)).drawString(drawn.capture(), anyFloat(), anyFloat(), anyInt(), anyBoolean());
        return drawn.getAllValues();
    }

    @Test
    void eachShaderScreenKeepsOneDrawContextSoItsTextIsMadeOnceRatherThanEveryFrame() {
        Umbra.initialize(home);
        Umbra.setShaderpackAndReload("Demo");
        GuiScreen parent = mock(GuiScreen.class);
        for (GuiScreen screen : List.of(new ShaderPackSelectScreen(parent), new ShaderPackConfigScreen(parent))) {
            screen.setWorldAndResolution(client, 480, 300);
            List<String> first = frameOf(screen);
            List<String> second = frameOf(screen);
            assertEquals(first, second);
            for (int i = 0; i < first.size(); i++) {
                assertSame(first.get(i), second.get(i), first.get(i));
            }
        }
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
        // Checks the pack's layout and lang.
        assertTrue(has(screen, "Preset"));
        assertTrue(has(screen, "§aQuality"));
        assertTrue(has(screen, "Leuchten"));
        assertTrue(has(screen, "Lighting & Sky"));
        assertTrue(has(screen, "§7MISSING"));
        assertEquals("[2]", Mixins.get(widget(screen, "§aQuality"), "value"));
        assertEquals("Glühend", Mixins.get(widget(screen, "Leuchten"), "value"));
        assertFalse((boolean) Mixins.get(widget(screen, "Apply"), "enabled"));
        // Checks the options cycle.
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
        // Checks Apply and Reset All.
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

        // Checks a pack without a layout.
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
