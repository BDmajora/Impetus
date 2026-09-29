package com.bdmajora.extras.client.budget;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.engine.impl.gui.widgets.FlatButtonWidget;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.impetus.impl.gui.FullscreenResolutions;
import com.bdmajora.impetus.impl.gui.ImpetusVideoOptionsScreen;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionsScreens;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuickSetupScreenTest {
    @TempDir
    static Path home;

    private static Minecraft client;
    private static GameSettings settings;

    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private MockedStatic<Display> display;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
        Mc.forge();
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 854;
        client.displayHeight = 480;
        OptionsScreens.prepare(client, home);
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
        display = Mockito.mockStatic(Display.class);
        // The full settings page this screen can open lists the monitor's modes
        display.when(Display::getAvailableDisplayModes).thenReturn(new DisplayMode[0]);
        Statics.set(FullscreenResolutions.class, "modes", null);
        Extras.options().renderBudget.quickSetupShown = false;
        Mockito.clearInvocations(client, client.fontRenderer);
    }

    @AfterEach
    void releaseNatives() {
        display.close();
        gl13.close();
        gl11.close();
        client.player = null;
        client.world = null;
        client.currentScreen = null;
    }

    private static Dim2i dim(Object entry) {
        FlatButtonWidget button = Mixins.get(entry, "button");
        return Mixins.get(button, "dim");
    }

    private static void click(QuickSetupScreen screen, Object entry) {
        Dim2i dim = dim(entry);
        screen.mouseClicked(dim.getCenterX(), dim.getCenterY(), 0);
    }

    @Test
    void opensOnceThePlayerHasSettledInAWorld() {
        TickEvent.ClientTickEvent end = new TickEvent.ClientTickEvent(TickEvent.Phase.END);
        QuickSetupScreen.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        QuickSetupScreen.onClientTick(end);
        EntityPlayerSP player = mock(EntityPlayerSP.class);
        player.ticksExisted = 10;
        client.player = player;
        client.world = mock(WorldClient.class);
        // Too soon after joining, then another screen is up
        QuickSetupScreen.onClientTick(end);
        player.ticksExisted = 40;
        client.currentScreen = mock(GuiScreen.class);
        QuickSetupScreen.onClientTick(end);
        verify(client, never()).displayGuiScreen(any());
        client.currentScreen = null;
        QuickSetupScreen.onClientTick(end);
        verify(client).displayGuiScreen(any(QuickSetupScreen.class));
        // Once answered it never comes back
        Extras.options().renderBudget.quickSetupShown = true;
        QuickSetupScreen.onClientTick(end);
        verify(client, times(1)).displayGuiScreen(any());
    }

    @Test
    void everyButtonSettlesTheQuestionForGood() {
        QuickSetupScreen screen = new QuickSetupScreen();
        screen.setWorldAndResolution(client, 400, 300);
        assertTrue(screen.doesGuiPauseGame());
        screen.drawWorldBackground(0);
        List<?> buttons = Mixins.get(screen, "buttons");
        assertEquals(5, buttons.size());

        // Hovering a button shows its help line under the rows
        Dim2i balanced = dim(buttons.get(0));
        screen.drawScreen(balanced.getCenterX(), balanced.getCenterY(), 0.0F);
        verify(client.fontRenderer).drawStringWithShadow(eq("impetus.quick_setup.balanced.help"), anyFloat(), anyFloat(), anyInt());
        screen.drawScreen(0, 0, 0.0F);
        verify(client.fontRenderer, times(2)).drawStringWithShadow(eq("impetus.quick_setup.title"), anyFloat(), anyFloat(), anyInt());
        // A click on nothing does nothing
        screen.mouseClicked(0, 0, 0);
        verify(client, never()).displayGuiScreen(any());

        // A profile turns the budget and the GPU booster on together under it
        ExtrasConfig options = Extras.options();
        click(screen, buttons.get(1));
        assertTrue(options.renderBudget.enabled);
        assertTrue(options.gpuBooster.enabled);
        assertEquals(ExtrasConfig.BudgetProfile.PERFORMANCE, options.renderBudget.profile);
        assertTrue(options.renderBudget.quickSetupShown);
        verify(client).displayGuiScreen(null);
        click(screen, buttons.get(2));
        assertEquals(ExtrasConfig.BudgetProfile.QUALITY, options.renderBudget.profile);
        click(screen, buttons.get(0));
        assertEquals(ExtrasConfig.BudgetProfile.BALANCED, options.renderBudget.profile);
        // Settings opens the full page, skip just closes
        click(screen, buttons.get(3));
        verify(client).displayGuiScreen(any(ImpetusVideoOptionsScreen.class));
        click(screen, buttons.get(4));
        verify(client, times(4)).displayGuiScreen(isNull());

        // Escape counts as "not now", other keys are ignored
        options.renderBudget.quickSetupShown = false;
        screen.keyTyped('x', Keyboard.KEY_X);
        assertFalse(options.renderBudget.quickSetupShown);
        screen.keyTyped((char) 0, Keyboard.KEY_ESCAPE);
        assertTrue(options.renderBudget.quickSetupShown);
        verify(client, times(5)).displayGuiScreen(isNull());
    }

    @Test
    void theKeysToggleTheOverlayAndOpenTheSettings() {
        KeyBinding toggle = Statics.get(RenderBudgetKeys.class, "TOGGLE_OVERLAY");
        KeyBinding open = Statics.get(RenderBudgetKeys.class, "OPEN_SETTINGS");
        RenderBudgetKeys.register();
        assertTrue(Arrays.asList(settings.keyBindings).containsAll(List.of(toggle, open)));

        // Every queued press flips the overlay once
        boolean overlay = Extras.options().renderBudget.overlay;
        Mixins.set(toggle, "pressTime", 2);
        RenderBudgetKeys.onKeyInput(new InputEvent.KeyInputEvent());
        assertEquals(overlay, Extras.options().renderBudget.overlay);
        Mixins.set(toggle, "pressTime", 1);
        RenderBudgetKeys.onKeyInput(new InputEvent.KeyInputEvent());
        assertEquals(!overlay, Extras.options().renderBudget.overlay);

        // The settings key opens the full page over whatever is showing
        client.currentScreen = mock(GuiScreen.class);
        Mixins.set(open, "pressTime", 1);
        RenderBudgetKeys.onKeyInput(new InputEvent.KeyInputEvent());
        verify(client).displayGuiScreen(any(ImpetusVideoOptionsScreen.class));
        assertNotNull(Mixins.construct(RenderBudgetKeys.class));
    }
}
