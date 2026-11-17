package com.bdmajora.impetus.umbra.gui;

import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionsScreens;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ShaderToggleKeyTest {
    @TempDir
    Path home;

    private Minecraft client;
    private GameSettings settings;
    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
        Mc.forge();
    }

    @BeforeEach
    void freshGame() throws IOException {
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        settings.language = "en_US";
        Mixins.set(client, "gameSettings", settings);
        OptionsScreens.prepare(client, home);
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        Path shaders = home.resolve("shaderpacks/Bare/shaders");
        Files.createDirectories(shaders);
        Files.writeString(shaders.resolve("final.fsh"), "#version 120\nvoid main() {}");
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
        client.world = null;
        client.ingameGUI = null;
    }

    // The translation key and arguments of the last action bar line
    private static TextComponentTranslation lastLine(GuiIngame hud, int times) {
        ArgumentCaptor<ITextComponent> line = ArgumentCaptor.forClass(ITextComponent.class);
        verify(hud, times(times)).setOverlayMessage(line.capture(), eq(false));
        return (TextComponentTranslation) line.getValue();
    }

    @Test
    void theKeyFlipsShadersKeepingThePackAndSaysWhatHappened() {
        // Checks the toggle key binding.
        KeyBinding toggle = Statics.get(ShaderToggleKey.class, "TOGGLE");
        assertEquals(Keyboard.KEY_K, toggle.getKeyCodeDefault());
        assertEquals(KeyConflictContext.IN_GAME, toggle.getKeyConflictContext());
        assertEquals("impetus.key.category", toggle.getKeyCategory());
        ShaderToggleKey.register();
        assertTrue(Arrays.asList(settings.keyBindings).contains(toggle));

        Umbra.initialize(home);
        GuiIngame hud = mock(GuiIngame.class);
        client.ingameGUI = hud;
        RenderGlobal renderGlobal = mock(RenderGlobal.class);
        client.renderGlobal = renderGlobal;

        // Before any pack was picked a press says so and changes nothing
        Mixins.set(toggle, "pressTime", 1);
        ShaderToggleKey.onKeyInput(new InputEvent.KeyInputEvent());
        assertEquals("impetus.shaders.none", lastLine(hud, 1).getKey());
        assertFalse(Umbra.areShadersEnabled());

        // Checks turning shaders off.
        Umbra.setShaderpackAndReload("Bare");
        assertEquals(Umbra.ToggleResult.DISABLED, ShaderToggleKey.toggle(client));
        TextComponentTranslation off = lastLine(hud, 2);
        assertEquals("impetus.shaders.off", off.getKey());
        assertArrayEquals(new Object[] {"Bare"}, off.getFormatArgs());
        verify(renderGlobal, never()).loadRenderers();

        // Checks turning shaders on in a world.
        client.world = mock(WorldClient.class);
        Mixins.set(toggle, "pressTime", 2);
        ShaderToggleKey.onKeyInput(new InputEvent.KeyInputEvent());
        assertFalse(Umbra.areShadersEnabled());
        verify(renderGlobal, times(2)).loadRenderers();
        assertEquals(Umbra.ToggleResult.ENABLED, ShaderToggleKey.toggle(client));
        assertEquals("Bare", Umbra.getSelectedPackName());
        assertNotNull(Umbra.getCurrentPack());
        TextComponentTranslation on = lastLine(hud, 5);
        assertEquals("impetus.shaders.on", on.getKey());
        assertArrayEquals(new Object[] {"Bare"}, on.getFormatArgs());
        verify(renderGlobal, times(3)).loadRenderers();

        // Checks a missing or broken remembered pack.
        Umbra.setShadersEnabled(false);
        Umbra.selectShaderpack("Gone", false);
        assertEquals(Umbra.ToggleResult.MISSING, ShaderToggleKey.toggle(client));
        assertEquals("impetus.shaders.missing", lastLine(hud, 6).getKey());
        verify(renderGlobal, times(3)).loadRenderers();
        assertEquals("impetus.shaders.failed", ((TextComponentTranslation) ShaderToggleKey.message(Umbra.ToggleResult.FAILED)).getKey());
        assertArrayEquals(new Object[] {"Gone"}, ((TextComponentTranslation) ShaderToggleKey.message(Umbra.ToggleResult.FAILED)).getFormatArgs());

        // Every outcome has its own line
        assertEquals(5, List.of(Umbra.ToggleResult.values()).stream()
                .map(result -> ((TextComponentTranslation) ShaderToggleKey.message(result)).getKey()).distinct().count());

        // With no HUD up the toggle still works
        client.ingameGUI = null;
        Umbra.selectShaderpack("Bare", false);
        assertEquals(Umbra.ToggleResult.ENABLED, ShaderToggleKey.toggle(client));
        assertEquals("Bare", Umbra.getSelectedPackName());
        assertNotNull(Mixins.construct(ShaderToggleKey.class));
    }
}
