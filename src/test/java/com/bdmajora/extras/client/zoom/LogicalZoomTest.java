package com.bdmajora.extras.client.zoom;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.zoom.EntityRendererZoomMixin;
import com.bdmajora.extras.mixin.zoom.ItemRendererZoomMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LogicalZoomTest {
    private ExtrasConfig config;
    private Minecraft client;
    private GameSettings settings;
    private KeyBinding key;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshState() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        Mixins.set(client, "gameSettings", settings);
        key = Statics.get(LogicalZoom.class, "ZOOM");
    }

    @AfterEach
    void releaseEverything() {
        Mixins.set(key, "pressed", false);
        Mixins.set(LogicalZoom.class, "zoomed", false);
        Mixins.set(LogicalZoom.class, "borrowedSmoothCamera", false);
        Mixins.set(LogicalZoom.class, "smoothCameraBefore", false);
        Mixins.set(Extras.class, "config", null);
    }

    private void hold(boolean down) {
        Mixins.set(key, "pressed", down);
        LogicalZoom.update();
    }

    // cot(fov / 2), the projection's y scale
    private static double projectionScale(float fov) {
        return 1.0D / Math.tan(Math.toRadians(fov) / 2.0D);
    }

    @Test
    void theDefaultsAreLogicalZooms() {
        assertTrue(config.zoom.enabled);
        assertEquals(23, config.zoom.level);
        assertTrue(config.zoom.smoothCamera);
        assertTrue(config.zoom.hideHand);
        assertEquals(Keyboard.KEY_C, key.getKeyCodeDefault());
        assertSame(KeyConflictContext.IN_GAME, key.getKeyConflictContext());
        assertEquals("impetus.key.category", key.getKeyCategory());

        // Registering puts the key on the controls screen
        LogicalZoom.register();
        assertTrue(Arrays.asList(settings.keyBindings).contains(key));
        assertNotNull(Mixins.construct(LogicalZoom.class));
    }

    @Test
    void holdingTheKeyScalesTheProjectionLikeTheOriginal() {
        hold(false);
        assertFalse(LogicalZoom.isZoomed());
        assertEquals(70.0F, LogicalZoom.zoomFov(70.0F));

        hold(true);
        assertTrue(LogicalZoom.isZoomed());
        // The original multiplies the projection's x and y by 1 / 0.23
        for (float fov : new float[] {30.0F, 70.0F, 110.0F}) {
            assertEquals(projectionScale(fov) / 0.23D, projectionScale(LogicalZoom.zoomFov(fov)), 1.0E-3D);
        }
        // A different strength follows the slider
        config.zoom.level = 50;
        assertEquals(projectionScale(70.0F) * 2.0D, projectionScale(LogicalZoom.zoomFov(70.0F)), 1.0E-3D);

        hold(false);
        assertFalse(LogicalZoom.isZoomed());
        assertEquals(70.0F, LogicalZoom.zoomFov(70.0F));
    }

    @Test
    void theCinematicCameraIsBorrowedForTheZoomAndHandedBack() {
        // Off before, on while held, off again after
        hold(true);
        assertTrue(settings.smoothCamera);
        hold(true);
        assertTrue(settings.smoothCamera);
        hold(false);
        assertFalse(settings.smoothCamera);

        // A player who already uses it keeps it
        settings.smoothCamera = true;
        hold(true);
        hold(false);
        assertTrue(settings.smoothCamera);

        // With the switch off the setting is never touched, even if the player flips it mid-zoom
        settings.smoothCamera = false;
        config.zoom.smoothCamera = false;
        hold(true);
        assertFalse(settings.smoothCamera);
        settings.smoothCamera = true;
        hold(false);
        assertTrue(settings.smoothCamera);
    }

    @Test
    void aScreenOrTheMasterSwitchEndsTheZoom() {
        hold(true);
        assertTrue(LogicalZoom.isZoomed());

        // The key is in-game only, so any open screen lets go of it
        client.currentScreen = mock(GuiScreen.class);
        LogicalZoom.update();
        assertFalse(LogicalZoom.isZoomed());
        assertFalse(settings.smoothCamera);

        client.currentScreen = null;
        LogicalZoom.update();
        assertTrue(LogicalZoom.isZoomed());
        config.zoom.enabled = false;
        LogicalZoom.update();
        assertFalse(LogicalZoom.isZoomed());
    }

    @Test
    void theHandHidesOnlyWhileZoomedAndAllowed() {
        assertFalse(LogicalZoom.hidesHand());
        hold(true);
        assertTrue(LogicalZoom.hidesHand());
        config.zoom.hideHand = false;
        assertFalse(LogicalZoom.hidesHand());
    }

    @Test
    void theMixinsLatchTheZoomNarrowTheWorldFovAndSkipTheHand() {
        EntityRendererZoomMixin renderer = Mixins.instance(EntityRendererZoomMixin.class);
        ItemRendererZoomMixin items = Mixins.instance(ItemRendererZoomMixin.class);

        CallbackInfo shown = Mixins.ci();
        Mixins.call(items, "impetus$hideHandWhenZoomed", 0.0F, shown);
        assertFalse(shown.isCancelled());

        Mixins.set(key, "pressed", true);
        Mixins.call(renderer, "impetus$updateZoom", 0.0F, 0L, Mixins.ci());
        assertTrue(LogicalZoom.isZoomed());

        // The world projection narrows; the hand's fixed projection does not
        assertEquals(LogicalZoom.zoomFov(70.0F), (float) Mixins.<Float>call(renderer, "impetus$zoomFov", 70.0F, true));
        assertTrue(LogicalZoom.zoomFov(70.0F) < 70.0F);
        assertEquals(70.0F, (float) Mixins.<Float>call(renderer, "impetus$zoomFov", 70.0F, false));

        CallbackInfo hidden = Mixins.ci();
        Mixins.call(items, "impetus$hideHandWhenZoomed", 0.0F, hidden);
        assertTrue(hidden.isCancelled());
    }
}
