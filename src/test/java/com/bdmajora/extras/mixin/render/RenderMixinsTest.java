package com.bdmajora.extras.mixin.render;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.ItemFrameLodState;
import com.bdmajora.extras.mixin.render.block_entity.TileEntityBeaconRendererMixin;
import com.bdmajora.extras.mixin.render.block_entity.TileEntityEnchantmentTableRendererMixin;
import com.bdmajora.extras.mixin.render.block_entity.TileEntityPistonRendererMixin;
import com.bdmajora.extras.mixin.render.block_entity.TileEntityRendererDispatcherBudgetMixin;
import com.bdmajora.extras.mixin.render.entity.ForgeHooksClientMixin;
import com.bdmajora.extras.mixin.render.entity.LayerCapeMixin;
import com.bdmajora.extras.mixin.render.entity.RenderEntityItemMixin;
import com.bdmajora.extras.mixin.render.entity.RenderItemFrameMixin;
import com.bdmajora.extras.mixin.render.entity.RenderItemMixin;
import com.bdmajora.extras.mixin.render.entity.RenderLivingBaseMixin;
import com.bdmajora.extras.mixin.render.entity.RenderPaintingMixin;
import com.bdmajora.extras.mixin.render.entity.RenderShadowMixin;
import com.bdmajora.extras.mixin.render.fog.EntityRendererFogFalloffMixin;
import com.bdmajora.extras.mixin.render.fog.EntityRendererFogMixin;
import com.bdmajora.extras.mixin.render.fog.EntityRendererVoidFogMixin;
import com.bdmajora.extras.mixin.render.sky.RenderGlobalSkyMixin;
import com.bdmajora.extras.mixin.render.sky.RenderGlobalStarsMixin;
import com.bdmajora.extras.mixin.render.sky.RenderGlobalSunMoonMixin;
import com.bdmajora.extras.mixin.render.weather.EntityRendererWeatherMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.MapItemRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.MapData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderMixinsTest {
    private ExtrasConfig config;
    private Minecraft client;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
        client = Mc.client();
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
        ItemFrameLodState.active = false;
    }

    // Whether the handler cancelled, called with its leading arguments and a fresh callback
    private static boolean cancels(Object mixin, String handler, Object... leading) {
        CallbackInfo ci = Mixins.ci();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = ci;
        Mixins.call(mixin, handler, args);
        return ci.isCancelled();
    }

    @Test
    void eachSwitchStopsItsRenderer() {
        LayerCapeMixin cape = Mixins.instance(LayerCapeMixin.class);
        RenderPaintingMixin painting = Mixins.instance(RenderPaintingMixin.class);
        TileEntityEnchantmentTableRendererMixin table = Mixins.instance(TileEntityEnchantmentTableRendererMixin.class);
        TileEntityPistonRendererMixin piston = Mixins.instance(TileEntityPistonRendererMixin.class);
        RenderGlobalSkyMixin sky = Mixins.instance(RenderGlobalSkyMixin.class);
        EntityRendererWeatherMixin weather = Mixins.instance(EntityRendererWeatherMixin.class);
        Object[] capeArgs = {null, 0F, 0F, 0F, 0F, 0F, 0F, 0F};
        Object[] entityArgs = {null, 0D, 0D, 0D, 0F, 0F};
        Object[] tileArgs = {null, 0D, 0D, 0D, 0F, 0, 1F};
        assertFalse(cancels(cape, "impetus$doRenderLayer", capeArgs));
        assertFalse(cancels(painting, "impetus$doRender", entityArgs));
        assertFalse(cancels(table, "impetus$render", tileArgs));
        assertFalse(cancels(piston, "impetus$render", tileArgs));
        assertFalse(cancels(sky, "impetus$renderSky", 0F, 0));
        assertFalse(cancels(weather, "impetus$renderRainSnow", 0F));
        config.detail.showCapes = false;
        config.render.paintings = false;
        config.render.enchantingTableBooks = false;
        config.render.pistons = false;
        config.detail.sky = false;
        config.detail.rainSnow = false;
        assertTrue(cancels(cape, "impetus$doRenderLayer", capeArgs));
        assertTrue(cancels(painting, "impetus$doRender", entityArgs));
        assertTrue(cancels(table, "impetus$render", tileArgs));
        assertTrue(cancels(piston, "impetus$render", tileArgs));
        assertTrue(cancels(sky, "impetus$renderSky", 0F, 0));
        assertTrue(cancels(weather, "impetus$renderRainSnow", 0F));

        // A dropped item stack draws one model when fancy stacks are off
        RenderEntityItemMixin items = Mixins.instance(RenderEntityItemMixin.class);
        CallbackInfoReturnable<Integer> fancy = Mixins.cir();
        Mixins.call(items, "impetus$modelCount", ItemStack.EMPTY, fancy);
        assertFalse(fancy.isCancelled());
        config.render.droppedItemsFancy = false;
        CallbackInfoReturnable<Integer> single = Mixins.cir();
        Mixins.call(items, "impetus$modelCount", ItemStack.EMPTY, single);
        assertEquals(1, single.getReturnValue());

        // The block entity budget hook defers to the controller, which is idle here
        TileEntityRendererDispatcherBudgetMixin budget = Mixins.instance(TileEntityRendererDispatcherBudgetMixin.class);
        assertFalse(cancels(budget, "impetus$budgetBlockEntity", new TileEntityBeacon(), 0F, -1));
        // As does the shadow hook
        RenderShadowMixin<?> shadow = Mixins.instance(RenderShadowMixin.class);
        assertFalse(cancels(shadow, "impetus$skipShadowOfCulledEntity", mock(EntityPig.class), 0D, 0D, 0D, 0F, 0F));
    }

    @Test
    void itemFramesDrawLessFromAfar() {
        RenderItemFrameMixin frames = Mixins.instance(RenderItemFrameMixin.class);
        EntityItemFrame frame = mock(EntityItemFrame.class);
        assertFalse(cancels(frames, "impetus$doRender", frame, 0D, 0D, 0D, 0F, 0F));
        assertFalse(cancels(frames, "impetus$renderName", frame, 0D, 0D, 0D));
        config.render.itemFrames = false;
        config.render.itemFrameNameTag = false;
        assertTrue(cancels(frames, "impetus$doRender", frame, 0D, 0D, 0D, 0F, 0F));
        assertTrue(cancels(frames, "impetus$renderName", frame, 0D, 0D, 0D));

        // The contents lose their sides and maps past the LOD line
        RenderManager renderManager = mock(RenderManager.class);
        when(client.getRenderManager()).thenReturn(renderManager);
        config.render.itemFrameLodDistance = 0;
        Mixins.call(frames, "impetus$beginLod", frame, Mixins.ci());
        assertFalse(ItemFrameLodState.active);
        config.render.itemFrameLodDistance = 16;
        Mixins.call(frames, "impetus$beginLod", frame, Mixins.ci());
        assertFalse(ItemFrameLodState.active);
        Entity viewer = mock(Entity.class);
        renderManager.renderViewEntity = viewer;
        when(frame.getDistanceSq(viewer)).thenReturn(400.0D);
        Mixins.call(frames, "impetus$beginLod", frame, Mixins.ci());
        assertTrue(ItemFrameLodState.active);
        MapItemRenderer maps = mock(MapItemRenderer.class);
        MapData data = mock(MapData.class);
        Mixins.call(frames, "impetus$cullFramedMap", maps, data, true);
        verify(maps, never()).renderMap(any(), Mockito.anyBoolean());
        Mixins.call(frames, "impetus$endLod", frame, Mixins.ci());
        assertFalse(ItemFrameLodState.active);
        Mixins.call(frames, "impetus$cullFramedMap", maps, data, true);
        verify(maps).renderMap(data, true);
        when(frame.getDistanceSq(viewer)).thenReturn(100.0D);
        Mixins.call(frames, "impetus$beginLod", frame, Mixins.ci());
        assertFalse(ItemFrameLodState.active);

        // Item quads go through the same filter from both the renderer and Forge's lit path
        IBakedModel model = mock(IBakedModel.class);
        List<BakedQuad> quads = List.of(mock(BakedQuad.class));
        when(model.getQuads(any(), any(), anyLong())).thenReturn(quads);
        assertSame(quads, Mixins.call(Mixins.instance(RenderItemMixin.class), "impetus$lodQuads", model, null, EnumFacing.UP, 0L));
        assertSame(quads, Mixins.call(ForgeHooksClientMixin.class, "impetus$lodQuads", model, null, EnumFacing.UP, 0L));
        assertNotNull(Mixins.instance(ForgeHooksClientMixin.class));
    }

    @Test
    void armorStandsAndPlayerNamesFollowTheirSwitches() throws ClassNotFoundException {
        // The renderer's static brightness texture is uploaded as the class initialises, so GL is stood in for that
        Mc.textures();
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            Class.forName("net.minecraft.client.renderer.entity.RenderLivingBase");
        }
        RenderLivingBaseMixin<?> living = Mixins.instance(RenderLivingBaseMixin.class, mock(RenderManager.class));
        AtomicReference<String> label = new AtomicReference<>();
        Mixins.stub(living, "canRenderName", invocation -> true);
        Mixins.stub(living, "renderLivingLabel", invocation -> {
            label.set(invocation.getArgument(1));
            return null;
        });
        EntityArmorStand stand = mock(EntityArmorStand.class);
        when(stand.getDisplayName()).thenReturn(new TextComponentString("Steve's stand"));
        assertFalse(cancels(living, "impetus$doRender", stand, 0D, 0D, 0D, 0F, 0F));
        assertFalse(cancels(living, "impetus$doRender", mock(EntityPig.class), 0D, 0D, 0D, 0F, 0F));
        // Hidden stands still show their name tag
        config.render.armorStands = false;
        assertTrue(cancels(living, "impetus$doRender", stand, 0D, 0D, 0D, 0F, 0F));
        assertEquals(new TextComponentString("Steve's stand").getFormattedText(), label.get());
        Mixins.stub(living, "canRenderName", invocation -> false);
        label.set(null);
        assertTrue(cancels(living, "impetus$doRender", stand, 0D, 0D, 0D, 0F, 0F));
        assertNull(label.get());

        AbstractClientPlayer player = mock(AbstractClientPlayer.class);
        CallbackInfoReturnable<Boolean> shown = Mixins.cir();
        Mixins.call(living, "impetus$canRenderName", player, shown);
        assertFalse(shown.isCancelled());
        config.render.playerNameTag = false;
        CallbackInfoReturnable<Boolean> hidden = Mixins.cir();
        Mixins.call(living, "impetus$canRenderName", player, hidden);
        assertEquals(false, hidden.getReturnValue());
        CallbackInfoReturnable<Boolean> mob = Mixins.cir();
        Mixins.call(living, "impetus$canRenderName", mock(EntityPig.class), mob);
        assertFalse(mob.isCancelled());
    }

    @Test
    void beaconBeamsStopAtTheTopOfTheWorld() {
        TileEntityBeaconRendererMixin renderer = Mixins.instance(TileEntityBeaconRendererMixin.class);
        config.render.limitBeaconBeamHeight = true;
        TileEntityBeacon beacon = new TileEntityBeacon();
        World world = mock(World.class);
        when(world.getHeight()).thenReturn(256);
        beacon.setWorld(world);
        beacon.setPos(new BlockPos(0, 64, 0));
        Object[] args = {beacon, 0D, 0D, 0D, 0F, 0, 1F};
        assertFalse(cancels(renderer, "impetus$beginRender", args));
        assertSame(beacon, Mixins.get(renderer, "impetus$currentBeacon"));

        Mc.Recorded<Void> segment = Mc.operation();
        Mixins.call(renderer, "impetus$limitBeamSegment", 0D, 0D, 0D, 0D, 1D, 0D, 0, 1024, new float[3], segment);
        assertEquals(192, segment.last()[7]);
        // Past the top nothing is drawn
        Mixins.call(renderer, "impetus$limitBeamSegment", 0D, 0D, 0D, 0D, 1D, 0D, 400, 1024, new float[3], segment);
        assertEquals(1, segment.count());
        // Without the limit, or outside a render, the segment is drawn as asked
        config.render.limitBeaconBeamHeight = false;
        Mixins.call(renderer, "impetus$limitBeamSegment", 0D, 0D, 0D, 0D, 1D, 0D, 0, 1024, new float[3], segment);
        assertEquals(1024, segment.last()[7]);
        Mixins.call(renderer, "impetus$endRender", args[0], 0D, 0D, 0D, 0F, 0, 1F, Mixins.ci());
        assertNull(Mixins.get(renderer, "impetus$currentBeacon"));
        config.render.limitBeaconBeamHeight = true;
        Mixins.call(renderer, "impetus$limitBeamSegment", 0D, 0D, 0D, 0D, 1D, 0D, 0, 1024, new float[3], segment);
        assertEquals(1024, segment.last()[7]);
        // Beacons switched off are not drawn at all
        config.render.beacons = false;
        assertTrue(cancels(renderer, "impetus$beginRender", args));
    }

    @Test
    void theSkyShowsOnlyWhatIsSwitchedOn() {
        RenderGlobalStarsMixin stars = Mixins.instance(RenderGlobalStarsMixin.class);
        Mc.Recorded<Float> brightness = Mc.operation(0.8F);
        assertEquals(0.8F, (float) Mixins.<Float>call(stars, "impetus$starBrightness", mock(WorldClient.class), 0F, brightness));
        config.detail.totalStars = 4000;
        assertEquals(4000, (int) Mixins.<Integer>call(stars, "impetus$starCount", 1500));
        config.detail.stars = false;
        assertEquals(0.0F, (float) Mixins.<Float>call(stars, "impetus$starBrightness", mock(WorldClient.class), 0F, brightness));
        assertEquals(1, brightness.count());

        RenderGlobalSunMoonMixin sunMoon = Mixins.instance(RenderGlobalSunMoonMixin.class);
        Tessellator tessellator = mock(Tessellator.class);
        BufferBuilder buffer = mock(BufferBuilder.class);
        when(tessellator.getBuffer()).thenReturn(buffer);
        Mc.Recorded<Void> draw = Mc.operation();
        Mixins.call(sunMoon, "impetus$drawSun", tessellator, draw);
        Mixins.call(sunMoon, "impetus$drawMoon", tessellator, draw);
        assertEquals(2, draw.count());
        // A hidden sun or moon still empties the tessellator, so the next draw starts clean
        config.detail.sun = false;
        config.detail.moon = false;
        Mixins.call(sunMoon, "impetus$drawSun", tessellator, draw);
        Mixins.call(sunMoon, "impetus$drawMoon", tessellator, draw);
        assertEquals(2, draw.count());
        verify(buffer, Mockito.times(2)).finishDrawing();
        verify(buffer, Mockito.times(2)).reset();
    }

    @Test
    void fogMovesWithTheSettingsButNeverHidesGameplayFog() {
        EntityRendererFogFalloffMixin falloff = Mixins.instance(EntityRendererFogFalloffMixin.class);
        Mixins.set(falloff, "farPlaneDistance", 256.0F);
        config.render.fogStart = 50;
        assertEquals(64.0F, (float) Mixins.<Float>call(falloff, "impetus$fogStart", 128.0F));
        assertEquals(128.0F, (float) Mixins.<Float>call(falloff, "impetus$fogEnd", 128.0F));
        config.render.fogDistance = 8;
        assertEquals(64.0F, (float) Mixins.<Float>call(falloff, "impetus$fogStart", 128.0F));
        assertEquals(144.0F, (float) Mixins.<Float>call(falloff, "impetus$fogEnd", 128.0F));
        // Blindness or being underwater keeps vanilla's fog
        Entity swimmer = mock(Entity.class);
        when(swimmer.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)).thenReturn(true);
        when(client.getRenderViewEntity()).thenReturn(swimmer);
        assertEquals(128.0F, (float) Mixins.<Float>call(falloff, "impetus$fogStart", 128.0F));
        assertEquals(128.0F, (float) Mixins.<Float>call(falloff, "impetus$fogEnd", 128.0F));
        when(client.getRenderViewEntity()).thenReturn(null);

        EntityRendererFogMixin fog = Mixins.instance(EntityRendererFogMixin.class);
        Mixins.call(fog, "impetus$disableFog", 0, 0F, Mixins.ci());
        config.render.fog = false;
        assertEquals(128.0F, (float) Mixins.<Float>call(falloff, "impetus$fogStart", 128.0F));
        assertEquals(128.0F, (float) Mixins.<Float>call(falloff, "impetus$fogEnd", 128.0F));
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            net.minecraft.client.renderer.GlStateManager.enableFog();
            Mixins.call(fog, "impetus$disableFog", 0, 0F, Mixins.ci());
            gl.verify(() -> GL11.glDisable(GL11.GL_FOG));
        }

        EntityRendererVoidFogMixin voidFog = Mixins.instance(EntityRendererVoidFogMixin.class);
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getVoidFogYFactor()).thenReturn(0.03125D);
        assertEquals(0.03125D, (double) Mixins.<Double>call(voidFog, "impetus$voidFogFactor", provider));
        config.detail.voidFog = false;
        assertEquals(1.0D, (double) Mixins.<Double>call(voidFog, "impetus$voidFogFactor", provider));
    }
}
