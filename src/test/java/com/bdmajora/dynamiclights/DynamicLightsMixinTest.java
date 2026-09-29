package com.bdmajora.dynamiclights;

import com.bdmajora.dynamiclights.client.DynamicLightHandlers;
import com.bdmajora.dynamiclights.client.DynamicLightSource;
import com.bdmajora.dynamiclights.client.DynamicLightsEngine;
import com.bdmajora.dynamiclights.mixin.DynamicLightsMixinPlugin;
import com.bdmajora.dynamiclights.mixin.GuiOverlayDebugMixin;
import com.bdmajora.dynamiclights.mixin.ItemRendererMixin;
import com.bdmajora.dynamiclights.mixin.MinecraftMixin;
import com.bdmajora.dynamiclights.mixin.ParticleMixin;
import com.bdmajora.dynamiclights.mixin.RenderGlobalMixin;
import com.bdmajora.dynamiclights.mixin.StateImplementationMixin;
import com.bdmajora.dynamiclights.mixin.TileEntityRendererDispatcherMixin;
import com.bdmajora.dynamiclights.mixin.WorldClientMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.EntityHangingMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.EntityLivingBaseMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.EntityMinecartMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.EntityMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.EntityPlayerMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.EntityTNTPrimedMixin;
import com.bdmajora.dynamiclights.mixin.lightsource.TileEntityMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicLightsMixinTest {
    private Minecraft client;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        client = Mc.client();
        // Rebuild scheduling goes through the client's renderer, which must carry the invoker mixin's interface
        Mixins.set(client, "renderGlobal", mock(RenderGlobal.class, Mockito.withSettings()
                .extraInterfaces(com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor.class)));
        Mixins.set(DynamicLights.class, "config", new DynamicLightsConfig());
        DynamicLights.engine().clearLightSources();
    }

    // A WorldClient, since Minecraft.world is typed as one and the mixins compare against it
    private static WorldClient clientWorld() {
        WorldClient world = mock(WorldClient.class);
        Mixins.set(world, "isRemote", true);
        return world;
    }

    @Test
    void theSubclassSourcesAreConstructibleWithTheirWorld() {
        // Each subclass mixin declares a constructor taking the world, which its target's own constructor stands in for
        // Entity's constructor asks the world which dimension it is
        WorldClient world = clientWorld();
        net.minecraft.world.WorldProvider provider = mock(net.minecraft.world.WorldProvider.class);
        Mixins.set(world, "provider", provider);
        assertNotNull(Mixins.instance(EntityLivingBaseMixin.class, world));
        assertNotNull(Mixins.instance(EntityPlayerMixin.class, world));
        assertNotNull(Mixins.instance(EntityMinecartMixin.class, world));
        assertNotNull(Mixins.instance(EntityTNTPrimedMixin.class, world));
        assertNotNull(Mixins.instance(EntityHangingMixin.class, world));
    }

    @Test
    void everyEntityIsAPotentialLightSource() {
        EntityMixin entity = Mixins.instance(EntityMixin.class);
        Entity self = (Entity) (Object) entity;
        WorldClient world = clientWorld();
        entity.world = world;
        entity.posX = 8.0D;
        entity.posY = 64.0D;
        entity.posZ = 8.0D;
        Mockito.doReturn(1.5F).when(self).getEyeHeight();
        assertEquals(8.0D, entity.impetus$getDynamicLightX());
        assertEquals(65.5D, entity.impetus$getDynamicLightY());
        assertEquals(8.0D, entity.impetus$getDynamicLightZ());
        assertSame(world, entity.impetus$getDynamicLightWorld());

        // A burning entity glows, a plain one does not, and the gates switch it off
        Mockito.doReturn(true).when(self).isBurning();
        entity.impetus$dynamicLightTick();
        assertEquals(14, entity.impetus$getLuminance());
        Mockito.doReturn(false).when(self).isBurning();
        entity.impetus$dynamicLightTick();
        assertEquals(0, entity.impetus$getLuminance());
        Mockito.doReturn(true).when(self).isBurning();
        DynamicLights.options().entitiesLightSource = false;
        entity.impetus$dynamicLightTick();
        assertEquals(0, entity.impetus$getLuminance());
        DynamicLights.options().entitiesLightSource = true;

        // The tick hook only runs client-side, and tracks the source once it is lit
        Mixins.call(entity, "impetus$onTick", Mixins.ci());
        assertTrue(DynamicLights.engine().containsLightSource(entity));
        entity.isDead = true;
        Mixins.call(entity, "impetus$onTick", Mixins.ci());
        assertFalse(DynamicLights.engine().containsLightSource(entity));
        entity.isDead = false;
        Mixins.set(world, "isRemote", false);
        Mixins.call(entity, "impetus$onTick", Mixins.ci());
        Mixins.set(world, "isRemote", true);

        // Rendering folds the source's own light into the entity's lightmap
        Mixins.call(entity, "impetus$onTick", Mixins.ci());
        var brightness = Mixins.<Integer>cir(0);
        Mixins.call(entity, "impetus$brightnessForRender", brightness);
        assertTrue(brightness.getReturnValue() > 0);
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        var off = Mixins.<Integer>cir(0);
        Mixins.call(entity, "impetus$brightnessForRender", off);
        assertEquals(0, off.getReturnValue());
        DynamicLights.options().mode = DynamicLightsMode.REALTIME;

        // Leaving the world stops the tracking
        entity.impetus$setDynamicLightEnabled(true);
        Mixins.call(entity, "impetus$onRemoved", Mixins.ci());
        assertFalse(DynamicLights.engine().containsLightSource(entity));

        // Moving or changing brightness re-lights the sections around the entity
        RenderGlobal renderer = mock(RenderGlobal.class,
                Mockito.withSettings().extraInterfaces(com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor.class));
        Mixins.set(client, "world", world);
        assertTrue(entity.impetus$shouldUpdateDynamicLight());
        assertTrue(entity.impetus$updateDynamicLight(renderer));
        assertFalse(entity.impetus$updateDynamicLight(renderer));
        entity.posX += 4.0D;
        assertTrue(entity.impetus$updateDynamicLight(renderer));
        entity.impetus$resetDynamicLight();
        assertTrue(entity.impetus$updateDynamicLight(renderer));
        // With no luminance the tracked sections are dropped and rebuilt one last time
        Mockito.doReturn(false).when(self).isBurning();
        entity.impetus$dynamicLightTick();
        assertTrue(entity.impetus$updateDynamicLight(renderer));
        entity.impetus$scheduleTrackedChunksRebuild(renderer);
        // A rate-limited mode refuses a second update in the same window
        DynamicLights.options().mode = DynamicLightsMode.SLOW;
        assertTrue(entity.impetus$shouldUpdateDynamicLight());
        assertFalse(entity.impetus$shouldUpdateDynamicLight());
        assertFalse(entity.impetus$updateDynamicLight(renderer));
    }

    @Test
    void livingEntitiesPlayersMinecartsAndTntEachHaveTheirOwnLuminance() {
        WorldClient world = clientWorld();
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());

        EntityLivingBaseMixin living = Mixins.instance(EntityLivingBaseMixin.class);
        net.minecraft.entity.EntityLivingBase livingSelf = (net.minecraft.entity.EntityLivingBase) (Object) living;
        Mixins.set(livingSelf, "world", world);
        Mockito.doReturn(new net.minecraft.util.math.Vec3d(0, 0, 0)).when(livingSelf).getPositionEyes(Mockito.anyFloat());
        Mockito.doReturn(List.of(new ItemStack(Blocks.GLOWSTONE))).when(livingSelf).getHeldEquipment();
        Mockito.doReturn(List.of()).when(livingSelf).getArmorInventoryList();
        Mockito.doReturn(false).when(livingSelf).isGlowing();
        Mockito.doReturn(false).when(livingSelf).isBurning();
        living.impetus$dynamicLightTick();
        assertEquals(14, living.impetus$getLuminance());
        Mockito.doReturn(true).when(livingSelf).isGlowing();
        living.impetus$dynamicLightTick();
        assertEquals(14, living.impetus$getLuminance());
        DynamicLights.options().entitiesLightSource = false;
        living.impetus$dynamicLightTick();
        assertEquals(0, living.impetus$getLuminance());
        DynamicLights.options().entitiesLightSource = true;

        EntityPlayerMixin player = Mixins.instance(EntityPlayerMixin.class);
        net.minecraft.entity.player.EntityPlayer playerSelf = (net.minecraft.entity.player.EntityPlayer) (Object) player;
        Mixins.set(playerSelf, "world", world);
        Mockito.doReturn(new net.minecraft.util.math.Vec3d(0, 0, 0)).when(playerSelf).getPositionEyes(Mockito.anyFloat());
        Mockito.doReturn(List.of(new ItemStack(Blocks.GLOWSTONE))).when(playerSelf).getHeldEquipment();
        Mockito.doReturn(List.of()).when(playerSelf).getArmorInventoryList();
        Mockito.doReturn(false).when(playerSelf).isGlowing();
        Mockito.doReturn(false).when(playerSelf).isBurning();
        // The first tick in a world always reports nothing, so a dimension change cannot leave light behind
        player.impetus$dynamicLightTick();
        assertEquals(0, player.impetus$getLuminance());
        player.impetus$dynamicLightTick();
        assertEquals(14, player.impetus$getLuminance());
        Mockito.doReturn(true).when(playerSelf).isSpectator();
        player.impetus$dynamicLightTick();
        assertEquals(0, player.impetus$getLuminance());
        Mockito.doReturn(false).when(playerSelf).isSpectator();
        Mockito.doReturn(true).when(playerSelf).isBurning();
        player.impetus$dynamicLightTick();
        assertEquals(14, player.impetus$getLuminance());
        // A player whose type the user switched off stops lighting anything
        com.bdmajora.dynamiclights.client.LightSourceSettings.getInstance()
                .loadDisabledEntities(new String[] {"minecraft:player"});
        com.bdmajora.dynamiclights.client.LightSourceSettings settings =
                com.bdmajora.dynamiclights.client.LightSourceSettings.getInstance();
        Mixins.<java.util.Map<Class<?>, String>>get(settings, "entityIds").put(playerSelf.getClass(), "minecraft:player");
        player.impetus$dynamicLightTick();
        assertEquals(0, player.impetus$getLuminance());
        settings.loadDisabledEntities(new String[0]);

        EntityMinecartMixin minecart = Mixins.instance(EntityMinecartMixin.class);
        net.minecraft.entity.item.EntityMinecart minecartSelf = (net.minecraft.entity.item.EntityMinecart) (Object) minecart;
        Mixins.set(minecartSelf, "world", world);
        // The shared DynamicLightSource half lives in the Entity mixin, which this one inherits from at runtime
        Mockito.doReturn(world).when((DynamicLightSource) minecart).impetus$getDynamicLightWorld();
        Mockito.doReturn(false).when(minecartSelf).isBurning();
        Mockito.doReturn(null).when(minecartSelf).getDisplayTile();
        minecart.impetus$dynamicLightTick();
        assertEquals(0, minecart.impetus$getLuminance());
        Mockito.doReturn(Blocks.GLOWSTONE.getDefaultState()).when(minecartSelf).getDisplayTile();
        minecart.impetus$dynamicLightTick();
        assertEquals(14, minecart.impetus$getLuminance());
        Mixins.call(minecart, "impetus$onMinecartTick", Mixins.ci());
        assertTrue(DynamicLights.engine().containsLightSource(minecart));
        DynamicLights.options().entitiesLightSource = false;
        minecart.impetus$dynamicLightTick();
        assertEquals(0, minecart.impetus$getLuminance());
        DynamicLights.options().entitiesLightSource = true;

        EntityTNTPrimedMixin tnt = Mixins.instance(EntityTNTPrimedMixin.class);
        net.minecraft.entity.item.EntityTNTPrimed tntSelf = (net.minecraft.entity.item.EntityTNTPrimed) (Object) tnt;
        Mixins.set(tntSelf, "world", world);
        Mockito.doReturn(world).when((DynamicLightSource) tnt).impetus$getDynamicLightWorld();
        Mockito.doReturn(false).when(tntSelf).isBurning();
        Mockito.doReturn(40).when(tntSelf).getFuse();
        Mixins.call(tnt, "impetus$captureStartFuse", world, 0.0D, 0.0D, 0.0D, null, Mixins.ci());
        // Fancy lighting ramps as the fuse burns down
        tnt.impetus$dynamicLightTick();
        assertEquals(0, tnt.impetus$getLuminance());
        Mockito.doReturn(20).when(tntSelf).getFuse();
        tnt.impetus$dynamicLightTick();
        assertEquals(8, tnt.impetus$getLuminance());
        DynamicLights.options().tntLighting = ExplosiveLightingMode.SIMPLE;
        tnt.impetus$dynamicLightTick();
        assertEquals(10, tnt.impetus$getLuminance());
        Mockito.doReturn(true).when(tntSelf).isBurning();
        tnt.impetus$dynamicLightTick();
        assertEquals(14, tnt.impetus$getLuminance());
        Mixins.call(tnt, "impetus$onTntTick", Mixins.ci());
        assertTrue(DynamicLights.engine().containsLightSource(tnt));
        DynamicLights.options().tntLighting = ExplosiveLightingMode.OFF;
        Mixins.call(tnt, "impetus$onTntTick", Mixins.ci());
        DynamicLights.options().entitiesLightSource = false;
        tnt.impetus$dynamicLightTick();
        assertEquals(0, tnt.impetus$getLuminance());
        DynamicLights.options().entitiesLightSource = true;

        EntityHangingMixin hanging = Mixins.instance(EntityHangingMixin.class);
        net.minecraft.entity.EntityHanging hangingSelf = (net.minecraft.entity.EntityHanging) (Object) hanging;
        Mixins.set(hangingSelf, "world", world);
        Mockito.doReturn(world).when((DynamicLightSource) hanging).impetus$getDynamicLightWorld();
        Mixins.call(hanging, "impetus$onHangingTick", Mixins.ci());
        Mixins.set(world, "isRemote", false);
        Mixins.call(hanging, "impetus$onHangingTick", Mixins.ci());
        Mixins.call(minecart, "impetus$onMinecartTick", Mixins.ci());
        Mixins.call(tnt, "impetus$onTntTick", Mixins.ci());
    }

    @Test
    void blockEntitiesLightThroughTheirHandler() {
        TileEntityMixin tile = Mixins.instance(TileEntityMixin.class);
        net.minecraft.tileentity.TileEntity self = (net.minecraft.tileentity.TileEntity) (Object) tile;
        WorldClient world = clientWorld();
        Mixins.set(tile, "world", world);
        Mixins.set(tile, "pos", new BlockPos(4, 64, 4));
        assertEquals(4.5D, tile.impetus$getDynamicLightX());
        assertEquals(64.5D, tile.impetus$getDynamicLightY());
        assertEquals(4.5D, tile.impetus$getDynamicLightZ());
        assertSame(world, tile.impetus$getDynamicLightWorld());

        // Without a registered handler a block entity stays dark
        tile.impetus$dynamicLightTick();
        assertEquals(0, tile.impetus$getLuminance());
        DynamicLightHandlers.registerTileEntityHandler(self.getClass(), any -> 9);
        tile.impetus$dynamicLightTick();
        assertEquals(9, tile.impetus$getLuminance());
        assertTrue(DynamicLights.engine().containsLightSource(tile));

        RenderGlobal renderer = mock(RenderGlobal.class,
                Mockito.withSettings().extraInterfaces(com.bdmajora.dynamiclights.mixin.RenderGlobalRebuildAccessor.class));
        Mixins.set(client, "world", world);
        assertTrue(tile.impetus$updateDynamicLight(renderer));
        assertFalse(tile.impetus$updateDynamicLight(renderer));
        tile.impetus$resetDynamicLight();
        assertTrue(tile.impetus$updateDynamicLight(renderer));
        tile.impetus$scheduleTrackedChunksRebuild(renderer);

        // Invalidating one stops the tracking, and an invalid or server-side one never ticks
        Mixins.call(tile, "impetus$onInvalidated", Mixins.ci());
        assertFalse(DynamicLights.engine().containsLightSource(tile));
        Mixins.set(tile, "tileEntityInvalid", true);
        tile.impetus$dynamicLightTick();
        Mixins.set(tile, "tileEntityInvalid", false);
        Mixins.set(tile, "world", null);
        tile.impetus$dynamicLightTick();
        // With a rate limit in force an update is refused until the delay elapses
        DynamicLights.options().mode = DynamicLightsMode.SLOW;
        assertTrue(tile.impetus$shouldUpdateDynamicLight());
        assertFalse(tile.impetus$shouldUpdateDynamicLight());
        assertFalse(tile.impetus$updateDynamicLight(renderer));
    }

    @Test
    void theClientSideHooksFeedTheEngine() {
        WorldClient world = clientWorld();
        // Loading a world drops every source from the old one
        MinecraftMixin minecraft = Mixins.instance(MinecraftMixin.class);
        DynamicLightSource source = mock(DynamicLightSource.class, Mockito.CALLS_REAL_METHODS);
        when(source.impetus$getDynamicLightWorld()).thenReturn(world);
        when(source.impetus$getLuminance()).thenReturn(4);
        DynamicLights.engine().addLightSource(source);
        Mixins.call(minecraft, "impetus$clearDynamicLights", null, "", Mixins.ci());
        assertEquals(0, DynamicLights.engine().getLightSourcesCount());

        // An entity leaving the client world is untracked
        WorldClientMixin clientWorld = Mixins.instance(WorldClientMixin.class);
        WorldClient self = (WorldClient) (Object) clientWorld;
        Entity plain = mock(Entity.class);
        Entity lit = mock(Entity.class, Mockito.withSettings().extraInterfaces(DynamicLightSource.class));
        Mockito.doReturn(lit).when(self).getEntityByID(1);
        Mockito.doReturn(plain).when(self).getEntityByID(2);
        Mixins.call(clientWorld, "impetus$untrackDynamicLight", 1, Mixins.cir());
        Mockito.verify((DynamicLightSource) lit).impetus$setDynamicLightEnabled(false);
        Mixins.call(clientWorld, "impetus$untrackDynamicLight", 2, Mixins.cir());

        // The render pass drives the engine's per-frame update
        RenderGlobalMixin renderGlobal = Mixins.instance(RenderGlobalMixin.class);
        Mixins.call(renderGlobal, "impetus$updateDynamicLights", null, null, 0.0F, Mixins.ci());

        // Particles and block entity renderers raise their light argument
        DynamicLights.engine().addLightSource(source);
        when(source.impetus$getDynamicLightX()).thenReturn(0.5D);
        when(source.impetus$getDynamicLightY()).thenReturn(0.5D);
        when(source.impetus$getDynamicLightZ()).thenReturn(0.5D);
        Args args = mock(Args.class);
        when(args.<BlockPos>get(0)).thenReturn(BlockPos.ORIGIN);
        when(args.<Integer>get(1)).thenReturn(1);
        Mixins.call(Mixins.instance(ParticleMixin.class), "impetus$dynamicParticleLight", args);
        Mockito.verify(args).set(1, 4);
        Args tileArgs = mock(Args.class);
        when(tileArgs.<BlockPos>get(0)).thenReturn(BlockPos.ORIGIN);
        when(tileArgs.<Integer>get(1)).thenReturn(1);
        Mixins.call(Mixins.instance(TileEntityRendererDispatcherMixin.class), "impetus$dynamicBlockEntityLight", tileArgs);
        Mockito.verify(tileArgs).set(1, 4);
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        Mixins.call(Mixins.instance(ParticleMixin.class), "impetus$dynamicParticleLight", args);
        Mixins.call(Mixins.instance(TileEntityRendererDispatcherMixin.class), "impetus$dynamicBlockEntityLight", tileArgs);
        Mockito.verifyNoMoreInteractions(Mockito.ignoreStubs(args));
    }

    @Test
    void theLightmapAndDebugOverlayReportDynamicLight() {
        WorldClient world = clientWorld();
        DynamicLightSource source = mock(DynamicLightSource.class, Mockito.CALLS_REAL_METHODS);
        when(source.impetus$getDynamicLightWorld()).thenReturn(world);
        when(source.impetus$getLuminance()).thenReturn(12);
        when(source.impetus$getDynamicLightX()).thenReturn(0.5D);
        when(source.impetus$getDynamicLightY()).thenReturn(0.5D);
        when(source.impetus$getDynamicLightZ()).thenReturn(0.5D);
        DynamicLights.engine().addLightSource(source);

        StateImplementationMixin state = Mixins.instance(StateImplementationMixin.class);
        net.minecraft.block.state.IBlockState self = (net.minecraft.block.state.IBlockState) (Object) state;
        Mockito.doReturn(false).when(self).isOpaqueCube();
        var lit = Mixins.<Integer>cir(0);
        Mixins.call(state, "impetus$addDynamicLight", mock(net.minecraft.world.IBlockAccess.class), BlockPos.ORIGIN, lit);
        assertEquals(12 << 4, lit.getReturnValue());
        // An opaque, unlit block is left alone, as is the empty access the chunk builder probes with
        Mockito.doReturn(true).when(self).isOpaqueCube();
        Mockito.doReturn(0).when(self).getLightValue(any(), any());
        var opaque = Mixins.<Integer>cir(0);
        Mixins.call(state, "impetus$addDynamicLight", mock(net.minecraft.world.IBlockAccess.class), BlockPos.ORIGIN, opaque);
        assertEquals(0, opaque.getReturnValue());
        var empty = Mixins.<Integer>cir(0);
        Mixins.call(state, "impetus$addDynamicLight",
                com.bdmajora.impetus.impl.util.EmptyBlockAccess.INSTANCE, BlockPos.ORIGIN, empty);
        assertEquals(0, empty.getReturnValue());
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        var off = Mixins.<Integer>cir(0);
        Mixins.call(state, "impetus$addDynamicLight", mock(net.minecraft.world.IBlockAccess.class), BlockPos.ORIGIN, off);
        assertEquals(0, off.getReturnValue());
        DynamicLights.options().mode = DynamicLightsMode.REALTIME;

        // The held item lights the hand, and so does anything else nearby
        ItemRendererMixin renderer = Mixins.instance(ItemRendererMixin.class);
        Mixins.set(renderer, "itemStackMainHand", new ItemStack(Blocks.GLOWSTONE));
        Mixins.set(renderer, "itemStackOffHand", ItemStack.EMPTY);
        Mixins.set(renderer, "mc", client);
        assertEquals(1, (int) Mixins.call(renderer, "impetus$handLightFloor", 1));
        net.minecraft.client.entity.EntityPlayerSP player = mock(net.minecraft.client.entity.EntityPlayerSP.class);
        Mixins.set(player, "world", world);
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.getDefaultState());
        when(player.getPositionEyes(Mockito.anyFloat())).thenReturn(new net.minecraft.util.math.Vec3d(0, 0, 0));
        Mixins.set(client, "player", player);
        assertEquals(14, (int) Mixins.call(renderer, "impetus$handLightFloor", 1));
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        assertEquals(1, (int) Mixins.call(renderer, "impetus$handLightFloor", 1));
        DynamicLights.options().mode = DynamicLightsMode.REALTIME;

        // The F3 overlay line, which says so when the feature is switched off
        GuiOverlayDebugMixin overlay = Mixins.instance(GuiOverlayDebugMixin.class);
        List<String> lines = new ArrayList<>();
        assertSame(lines, Mixins.call(overlay, "impetus$addDynamicLightInfo", lines));
        assertTrue(lines.isEmpty());
        DynamicLights.options().showDebugInfo = true;
        Mixins.call(overlay, "impetus$addDynamicLightInfo", lines);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches("Dynamic Light Sources: \\d+ \\(U: \\d+\\)"));
        DynamicLights.options().mode = DynamicLightsMode.OFF;
        Mixins.call(overlay, "impetus$addDynamicLightInfo", lines);
        assertTrue(lines.get(1).contains("Disabled"));

        // The plugin applies everything; the mode switch is read at call time instead
        DynamicLightsMixinPlugin plugin = new DynamicLightsMixinPlugin();
        assertTrue(plugin.shouldApplyMixin("net.minecraft.entity.Entity",
                "com.bdmajora.dynamiclights.mixin.lightsource.EntityMixin"));
    }
}
