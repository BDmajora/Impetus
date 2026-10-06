package com.bdmajora.extras.mixin;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.DriverLimits;
import com.bdmajora.extras.client.ThreadTuning;
import com.bdmajora.extras.mixin.animation.TextureMapMixin;
import com.bdmajora.extras.mixin.atlas.MinecraftTextureSizeMixin;
import com.bdmajora.extras.mixin.atlas.SplashProgressTextureSizeMixin;
import com.bdmajora.extras.mixin.biome_colors.BiomeColorHelperMixin;
import com.bdmajora.extras.mixin.leaves.BlockLeavesCullMixin;
import com.bdmajora.extras.mixin.light_updates.WorldMixin;
import com.bdmajora.extras.mixin.lightmap.EntityRendererLightmapMixin;
import com.bdmajora.extras.mixin.loading.MinecraftLoadWorldGcMixin;
import com.bdmajora.extras.mixin.loading.NetHandlerPlayClientRespawnMixin;
import com.bdmajora.extras.mixin.loading.ScreenShotHelperMixin;
import com.bdmajora.extras.mixin.misc.GuiIngameMixin;
import com.bdmajora.extras.mixin.misc.MinecraftServerMixin;
import com.bdmajora.extras.mixin.misc.WorldServerMixin;
import com.bdmajora.extras.mixin.models.ModelRendererMatrixMixin;
import com.bdmajora.extras.mixin.panini.EntityRendererPaniniMixin;
import com.bdmajora.extras.mixin.prevent_shaders.EntityRendererPreventShadersMixin;
import com.bdmajora.extras.mixin.profiler.RenderManagerMixin;
import com.bdmajora.extras.mixin.profiler.TileEntityRendererDispatcherMixin;
import com.bdmajora.extras.mixin.sky_colors.BiomeMixin;
import com.bdmajora.extras.mixin.sky_colors.BiomeSwampMixin;
import com.bdmajora.extras.mixin.steady_debug_hud.GuiOverlayDebugMixin;
import com.bdmajora.extras.mixin.threads.MinecraftServerThreadMixin;
import com.bdmajora.extras.mixin.threads.MinecraftYieldMixin;
import com.bdmajora.extras.mixin.toast.GuiToastMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.bdmajora.testing.Statics;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.toasts.AdvancementToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.gui.toasts.RecipeToast;
import net.minecraft.client.gui.toasts.SystemToast;
import net.minecraft.client.gui.toasts.TutorialToast;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldProviderSurface;
import net.minecraftforge.fml.common.asm.FMLSanityChecker;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmallMixinsTest {
    @TempDir
    Path dir;

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
        ShadowStubs.clear();
        ThreadTuning.removeRenderYield = false;
    }

    private static boolean cancels(Object mixin, String handler, Object... leading) {
        CallbackInfo ci = Mixins.ci();
        Object[] args = java.util.Arrays.copyOf(leading, leading.length + 1);
        args[leading.length] = ci;
        Mixins.call(mixin, handler, args);
        return ci.isCancelled();
    }

    private static TextureAtlasSprite sprite(String name) {
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", name);
        return sprite;
    }

    @Test
    void animationsFollowTheirCategorySwitch() {
        TextureMapMixin atlas = Mixins.instance(TextureMapMixin.class);
        Mc.Recorded<Void> animate = Mc.operation();
        // Anything goes with the defaults, including a missing sprite or name
        Mixins.call(atlas, "impetus$controlAnimation", null, animate);
        Mixins.call(atlas, "impetus$controlAnimation", sprite(null), animate);
        Mixins.call(atlas, "impetus$controlAnimation", sprite("modded:blocks/gear"), animate);
        assertEquals(3, animate.count());

        String[] names = {"water_still", "water_flow", "water_overlay", "lava_still", "lava_flow", "fire_layer_0", "fire_layer_1",
                "portal", "redstone", "explosion", "flame", "smoke", "sculk_sensor"};
        ExtrasConfig.AnimationSettings settings = config.animation;
        settings.water = settings.lava = settings.fire = settings.portal = settings.redstone = false;
        settings.explosion = settings.flame = settings.smoke = settings.sculkSensor = settings.blockAnimations = false;
        Mc.Recorded<Void> off = Mc.operation();
        for (String name : names) {
            Mixins.call(atlas, "impetus$controlAnimation", sprite("minecraft:blocks/" + name), off);
        }
        Mixins.call(atlas, "impetus$controlAnimation", sprite("modded:blocks/gear"), off);
        assertEquals(0, off.count());
        settings.all = false;
        Mixins.call(atlas, "impetus$controlAnimation", null, off);
        assertEquals(0, off.count());
    }

    @Test
    void modelPartsApplyTheirTransformAsOneMatrix() {
        ModelRendererMatrixMixin part = Mixins.instance(ModelRendererMatrixMixin.class);
        Mixins.set(part, "showModel", true);
        Mixins.set(part, "displayList", 5);
        ModelRenderer child = mock(ModelRenderer.class);
        Mixins.set(part, "childModels", new ArrayList<>(List.of(child)));
        Mixins.stub(part, "compileDisplayList", invocation -> {
            Mixins.set(part, "compiled", true);
            return null;
        });
        // Off, vanilla renders
        config.entityModels.matrixTransforms = false;
        assertFalse(cancels(part, "impetus$renderWithMatrix", 0.0625F));
        assertFalse(cancels(part, "impetus$renderWithRotationMatrix", 0.0625F));
        assertFalse(cancels(part, "impetus$postRenderMatrix", 0.0625F));
        config.entityModels.matrixTransforms = true;
        try (MockedStatic<GL11> gl = Mockito.mockStatic(GL11.class)) {
            // No transform at all draws the list and the children in place
            assertTrue(cancels(part, "impetus$renderWithMatrix", 0.0625F));
            gl.verify(() -> GL11.glCallList(5));
            verify(child).render(0.0625F);
            // A pivot only translates
            Mixins.set(part, "rotationPointX", 1.0F);
            assertTrue(cancels(part, "impetus$renderWithMatrix", 0.0625F));
            gl.verify(() -> GL11.glTranslatef(0.0625F, 0.0F, 0.0F), Mockito.atLeastOnce());
            assertTrue(cancels(part, "impetus$postRenderMatrix", 0.0625F));
            // Rotations become one matrix, in the order each call site uses
            Mixins.set(part, "rotateAngleX", 0.3F);
            Mixins.set(part, "rotateAngleY", 0.2F);
            Mixins.set(part, "rotateAngleZ", 0.1F);
            Mixins.set(part, "offsetX", 0.5F);
            assertTrue(cancels(part, "impetus$renderWithMatrix", 0.0625F));
            assertTrue(cancels(part, "impetus$renderWithRotationMatrix", 0.0625F));
            assertTrue(cancels(part, "impetus$postRenderMatrix", 0.0625F));
            gl.verify(() -> GL11.glMultMatrix(any(java.nio.FloatBuffer.class)), Mockito.times(3));
            // An untransformed postRender does nothing at all
            Mixins.set(part, "rotateAngleX", 0.0F);
            Mixins.set(part, "rotateAngleY", 0.0F);
            Mixins.set(part, "rotateAngleZ", 0.0F);
            Mixins.set(part, "rotationPointX", 0.0F);
            assertTrue(cancels(part, "impetus$postRenderMatrix", 0.0625F));
            // Nothing for a hidden part, and compiling happens once
            Mixins.set(part, "compiled", false);
            assertTrue(cancels(part, "impetus$renderWithRotationMatrix", 0.0625F));
            Mixins.set(part, "compiled", false);
            assertTrue(cancels(part, "impetus$postRenderMatrix", 0.0625F));
            Mixins.set(part, "childModels", null);
            assertTrue(cancels(part, "impetus$renderWithMatrix", 0.0625F));
            Mixins.set(part, "isHidden", true);
            assertTrue(cancels(part, "impetus$renderWithMatrix", 0.0625F));
            assertTrue(cancels(part, "impetus$renderWithRotationMatrix", 0.0625F));
            assertTrue(cancels(part, "impetus$postRenderMatrix", 0.0625F));
        }
    }

    @Test
    void biomeTintsCanBeFlattened() {
        BiomeMixin biome = Mixins.instance(BiomeMixin.class);
        assertEquals(0.2F, (float) Mixins.<Float>call(biome, "impetus$uniformSkyColor", 0.2F));
        config.detail.skyColors = false;
        assertEquals(0.8F, (float) Mixins.<Float>call(biome, "impetus$uniformSkyColor", 0.2F));

        BiomeSwampMixin swamp = Mixins.instance(BiomeSwampMixin.class);
        CallbackInfoReturnable<Integer> grass = Mixins.cir();
        Mixins.call(swamp, "impetus$grassColor", BlockPos.ORIGIN, grass);
        assertFalse(grass.isCancelled());
        config.detail.swampColors = false;
        CallbackInfoReturnable<Integer> plainGrass = Mixins.cir();
        Mixins.call(swamp, "impetus$grassColor", BlockPos.ORIGIN, plainGrass);
        assertTrue(plainGrass.isCancelled());
        CallbackInfoReturnable<Integer> foliage = Mixins.cir();
        Mixins.call(swamp, "impetus$foliageColor", BlockPos.ORIGIN, foliage);
        assertTrue(foliage.isCancelled());
        config.detail.swampColors = true;
        CallbackInfoReturnable<Integer> swampFoliage = Mixins.cir();
        Mixins.call(swamp, "impetus$foliageColor", BlockPos.ORIGIN, swampFoliage);
        assertFalse(swampFoliage.isCancelled());

        assertNotNull(Mixins.instance(BiomeColorHelperMixin.class));
        for (String handler : new String[] {"impetus$grassColor", "impetus$waterColor", "impetus$foliageColor"}) {
            CallbackInfoReturnable<Integer> tinted = Mixins.cir();
            Mixins.call(BiomeColorHelperMixin.class, handler, tinted);
            assertFalse(tinted.isCancelled());
        }
        config.detail.biomeColors = false;
        for (String handler : new String[] {"impetus$grassColor", "impetus$waterColor", "impetus$foliageColor"}) {
            CallbackInfoReturnable<Integer> flat = Mixins.cir();
            Mixins.call(BiomeColorHelperMixin.class, handler, flat);
            assertTrue(flat.isCancelled());
        }
    }

    private static IBlockAccess leafWorld(Map<BlockPos, IBlockState> states) {
        IBlockAccess world = mock(IBlockAccess.class);
        when(world.getBlockState(any())).thenAnswer(invocation ->
                states.getOrDefault(invocation.<BlockPos>getArgument(0).toImmutable(), Blocks.AIR.getDefaultState()));
        return world;
    }

    private static Boolean cull(BlockLeavesCullMixin leaves, IBlockAccess world, EnumFacing side) {
        CallbackInfoReturnable<Boolean> cir = Mixins.cir();
        Mixins.call(leaves, "impetus$cullInteriorFaces", Blocks.LEAVES.getDefaultState(), world, BlockPos.ORIGIN, side, cir);
        return cir.isCancelled() ? cir.getReturnValue() : null;
    }

    @Test
    void interiorLeafFacesAreCulledByTheChosenRule() {
        BlockLeavesCullMixin leaves = Mixins.instance(BlockLeavesCullMixin.class);
        IBlockState leaf = Blocks.LEAVES.getDefaultState();
        Map<BlockPos, IBlockState> surrounded = new java.util.HashMap<>();
        for (EnumFacing facing : EnumFacing.VALUES) {
            surrounded.put(BlockPos.ORIGIN.offset(facing), leaf);
        }
        surrounded.put(BlockPos.ORIGIN.offset(EnumFacing.UP, 2), Blocks.STONE.getDefaultState());
        IBlockAccess inside = leafWorld(surrounded);
        // The default rule, or fast leaves, leave vanilla alone
        assertNull(cull(leaves, inside, EnumFacing.UP));
        config.leaves.cullingMode = ExtrasConfig.LeafCulling.CHECK;
        Mixins.set(leaves, "leavesFancy", false);
        assertNull(cull(leaves, inside, EnumFacing.UP));
        Mixins.set(leaves, "leavesFancy", true);
        // Check drops a face only when every neighbour hides
        assertEquals(false, cull(leaves, inside, EnumFacing.UP));
        Map<BlockPos, IBlockState> edge = new java.util.HashMap<>(surrounded);
        edge.remove(BlockPos.ORIGIN.offset(EnumFacing.DOWN));
        assertNull(cull(leaves, leafWorld(edge), EnumFacing.UP));
        // An open neighbour on the face itself always draws
        assertNull(cull(leaves, leafWorld(Map.of()), EnumFacing.UP));
        // Depth looks through the canopy for air
        config.leaves.cullingMode = ExtrasConfig.LeafCulling.DEPTH;
        config.leaves.cullingDepth = 1;
        assertEquals(false, cull(leaves, inside, EnumFacing.UP));
        assertNull(cull(leaves, inside, EnumFacing.NORTH));
    }

    @Test
    void theDriverAtlasLimitIsHandedToVanillaAndForge() {
        Statics.set(FMLSanityChecker.class, "fmlLocation", dir.toFile());
        MinecraftTextureSizeMixin minecraft = Mixins.instance(MinecraftTextureSizeMixin.class);
        assertNotNull(minecraft);
        Mixins.set(DriverLimits.class, "maxTextureSize", 8192);
        try {
            CallbackInfoReturnable<Integer> size = Mixins.cir();
            Mixins.call(MinecraftTextureSizeMixin.class, "impetus$queryDriverLimit", size);
            assertEquals(8192, size.getReturnValue());
            CallbackInfoReturnable<Integer> splash = Mixins.cir();
            Mixins.call(SplashProgressTextureSizeMixin.class, "impetus$queryDriverLimit", splash);
            assertEquals(8192, splash.getReturnValue());
            config.loading.driverAtlasLimit = false;
            CallbackInfoReturnable<Integer> probe = Mixins.cir();
            Mixins.call(MinecraftTextureSizeMixin.class, "impetus$queryDriverLimit", probe);
            assertFalse(probe.isCancelled());
            CallbackInfoReturnable<Integer> splashProbe = Mixins.cir();
            Mixins.call(SplashProgressTextureSizeMixin.class, "impetus$queryDriverLimit", splashProbe);
            assertFalse(splashProbe.isCancelled());
            assertNotNull(Mixins.instance(SplashProgressTextureSizeMixin.class));
        } finally {
            Mixins.set(DriverLimits.class, "maxTextureSize", -1);
        }
    }

    @Test
    void cameraHooksAndSimpleSwitches() {
        EntityRendererPaniniMixin panini = Mixins.instance(EntityRendererPaniniMixin.class);
        Mc.Recorded<Void> perspective = Mc.operation();
        Mixins.call(panini, "impetus$captureProjection", 90.0F, 2.0F, 0.05F, 256.0F, perspective);
        assertEquals(1, perspective.count());
        Mixins.call(panini, "impetus$applyPanini", 0.0F, 0L, Mixins.ci());

        EntityRendererPreventShadersMixin shaders = Mixins.instance(EntityRendererPreventShadersMixin.class);
        assertFalse(cancels(shaders, "impetus$preventLoadShader", (Object) null));
        assertFalse(cancels(shaders, "impetus$preventSwitchUseShader"));
        config.render.preventShaders = true;
        assertTrue(cancels(shaders, "impetus$preventLoadShader", (Object) null));
        assertTrue(cancels(shaders, "impetus$preventSwitchUseShader"));

        GuiIngameMixin ingame = Mixins.instance(GuiIngameMixin.class);
        assertFalse(cancels(ingame, "impetus$renderSelectedItem", (Object) null));
        config.detail.heldItemTooltips = false;
        assertTrue(cancels(ingame, "impetus$renderSelectedItem", (Object) null));

        MinecraftServerMixin server = Mixins.instance(MinecraftServerMixin.class);
        assertEquals(ExtrasConfig.ExtraSettings.AUTOSAVE_VANILLA_TICKS, (int) Mixins.<Integer>call(server, "impetus$autosaveInterval", 900));
        config.extra.autosaveInterval = 0;
        assertEquals(Integer.MAX_VALUE, (int) Mixins.<Integer>call(server, "impetus$autosaveInterval", 900));

        WorldServerMixin worldServer = Mixins.instance(WorldServerMixin.class);
        Mixins.call(worldServer, "impetus$applyOverrides", Mixins.ci());

        MinecraftServerThreadMixin serverThread = Mixins.instance(MinecraftServerThreadMixin.class);
        Mixins.call(serverThread, "impetus$announceServerThread", Mixins.ci());
        assertSame(Thread.currentThread(), Mixins.get(ThreadTuning.class, "serverThread"));
        Mixins.call(serverThread, "impetus$forgetServerThread", Mixins.ci());
        assertNull(Mixins.get(ThreadTuning.class, "serverThread"));
        MinecraftYieldMixin yield = Mixins.instance(MinecraftYieldMixin.class);
        Mixins.call(yield, "impetus$optionalYield");
        ThreadTuning.removeRenderYield = true;
        Mixins.call(yield, "impetus$optionalYield");

        WorldMixin world = Mixins.instance(WorldMixin.class);
        CallbackInfoReturnable<Boolean> server1 = Mixins.cir();
        Mixins.call(world, "impetus$checkLightFor", EnumSkyBlock.BLOCK, BlockPos.ORIGIN, server1);
        assertFalse(server1.isCancelled());
        Mixins.set(world, "isRemote", true);
        CallbackInfoReturnable<Boolean> client1 = Mixins.cir();
        Mixins.call(world, "impetus$checkLight", BlockPos.ORIGIN, client1);
        assertFalse(client1.isCancelled());
        config.render.lightUpdates = false;
        CallbackInfoReturnable<Boolean> frozen = Mixins.cir();
        Mixins.call(world, "impetus$checkLightFor", EnumSkyBlock.SKY, BlockPos.ORIGIN, frozen);
        assertEquals(false, frozen.getReturnValue());
        CallbackInfoReturnable<Boolean> frozenToo = Mixins.cir();
        Mixins.call(world, "impetus$checkLight", BlockPos.ORIGIN, frozenToo);
        assertEquals(false, frozenToo.getReturnValue());
        assertNotNull(new ExtrasMixinPlugin());
    }

    @Test
    void toastsAreFilteredByKind() {
        GuiToastMixin toasts = Mixins.instance(GuiToastMixin.class);
        List<IToast> kinds = List.of(Mc.uninitialized(AdvancementToast.class), Mc.uninitialized(RecipeToast.class),
                Mc.uninitialized(TutorialToast.class), Mc.uninitialized(SystemToast.class));
        for (IToast toast : kinds) {
            assertFalse(cancels(toasts, "impetus$filterToast", toast));
        }
        config.extra.toastAdvancement = false;
        config.extra.toastRecipe = false;
        config.extra.toastTutorial = false;
        config.extra.toastSystem = false;
        for (IToast toast : kinds) {
            assertTrue(cancels(toasts, "impetus$filterToast", toast));
        }
        config.extra.toasts = false;
        assertTrue(cancels(toasts, "impetus$filterToast", mock(IToast.class)));
    }

    @Test
    void rendererProfilingWrapsEachDraw() {
        RenderManagerMixin renderManager = Mixins.instance(RenderManagerMixin.class);
        EntityPig pig = mock(EntityPig.class);
        WorldClient world = mock(WorldClient.class);
        Mixins.set(world, "profiler", new net.minecraft.profiler.Profiler());
        Mixins.set(pig, "world", world);
        config.render.profileEntityRendering = true;
        doReturnRender(renderManager);
        Mixins.call(renderManager, "impetus$beginRenderEntity", pig, 0D, 0D, 0D, 0F, 0F, false, Mixins.ci());
        Mixins.call(renderManager, "impetus$endRenderEntity", pig, 0D, 0D, 0D, 0F, 0F, false, Mixins.ci());
        TileEntityRendererDispatcherMixin dispatcher = Mixins.instance(TileEntityRendererDispatcherMixin.class);
        TileEntityChest chest = new TileEntityChest();
        Mixins.call(dispatcher, "impetus$beginRender", chest, 0D, 0D, 0D, 0F, -1, 1F, Mixins.ci());
        Mixins.call(dispatcher, "impetus$endRender", chest, 0D, 0D, 0D, 0F, -1, 1F, Mixins.ci());
    }

    @SuppressWarnings("unchecked")
    private static void doReturnRender(RenderManagerMixin renderManager) {
        Mockito.doReturn(mock(Render.class)).when(renderManager).getEntityRenderObject(any());
    }

    // A dimension that brings its own lightmap colours, which the cache must not freeze
    static class LitProvider extends WorldProviderSurface {
        @Override
        public void getLightmapColors(float partialTicks, float sunBrightness, float skyLight, float blockLight, float[] colors) {
        }
    }

    @Test
    void theLightmapIsOnlyRebuiltWhenWhatItReadsChanges() {
        EntityRendererLightmapMixin renderer = Mixins.instance(EntityRendererLightmapMixin.class);
        Mixins.set(renderer, "mc", client);
        Mixins.set(renderer, "impetus$lightmapKey", Long.MIN_VALUE);
        Mixins.set(client, "gameSettings", mock(GameSettings.class));
        WorldClient world = mock(WorldClient.class);
        Mixins.set(world, "provider", new WorldProviderSurface());
        when(world.getSunBrightness(anyFloat())).thenReturn(0.5F);
        EntityPlayerSP player = mock(EntityPlayerSP.class);

        // Nothing to cache without an update pending, with caching off, or without a world
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        Mixins.set(renderer, "lightmapUpdateNeeded", true);
        config.clientTick.lightmapCaching = false;
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        config.clientTick.lightmapCaching = true;
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        Mixins.set(client, "world", world);
        Mixins.set(client, "player", player);

        // The first frame builds and remembers; an unchanged second frame is skipped
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        assertTrue(cancels(renderer, "impetus$skipUnchanged", 0F));
        assertFalse((boolean) Mixins.<Boolean>get(renderer, "lightmapUpdateNeeded"));
        // Night vision, fading or not, is part of what it reads
        Mixins.set(renderer, "lightmapUpdateNeeded", true);
        when(player.getActivePotionEffect(MobEffects.NIGHT_VISION)).thenReturn(new PotionEffect(MobEffects.NIGHT_VISION, 1000));
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        when(player.getActivePotionEffect(MobEffects.NIGHT_VISION)).thenReturn(new PotionEffect(MobEffects.NIGHT_VISION, 100));
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        // A boss fading the sky, or a dimension with its own colours, always rebuilds
        Mixins.set(renderer, "bossColorModifier", 0.5F);
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        Mixins.set(renderer, "bossColorModifier", 0.0F);
        Mixins.set(world, "provider", new LitProvider());
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
        assertFalse(cancels(renderer, "impetus$skipUnchanged", 0F));
    }

    @Test
    void theDebugScreenRebuildsOnlyOnItsInterval() {
        GuiOverlayDebugMixin debug = Mixins.instance(GuiOverlayDebugMixin.class);
        Mixins.set(debug, "impetus$leftCache", new ArrayList<String>());
        Mixins.set(debug, "impetus$rightCache", new ArrayList<String>());
        // Off, every frame rebuilds
        config.extra.steadyDebugHud = false;
        Mixins.call(debug, "impetus$decideRebuild", null, Mixins.ci());
        CallbackInfoReturnable<List<String>> live = Mixins.cir(new ArrayList<>(List.of("fps")));
        Mixins.call(debug, "impetus$leftFromCache", live);
        assertFalse(live.isCancelled());
        Mixins.call(debug, "impetus$cacheLeft", live);
        CallbackInfoReturnable<List<String>> right = Mixins.cir(new ArrayList<>(List.of("java")));
        Mixins.call(debug, "impetus$rightFromCache", right);
        Mixins.call(debug, "impetus$cacheRight", right);

        config.extra.steadyDebugHud = true;
        config.extra.steadyDebugHudRefreshInterval = 20;
        Mixins.set(debug, "impetus$nextUpdateNanos", 0L);
        Mixins.call(debug, "impetus$decideRebuild", null, Mixins.ci());
        Mixins.call(debug, "impetus$cacheLeft", live);
        Mixins.call(debug, "impetus$cacheRight", right);
        // Within the interval the columns come from the cache, as copies
        Mixins.call(debug, "impetus$decideRebuild", null, Mixins.ci());
        CallbackInfoReturnable<List<String>> cachedLeft = Mixins.cir();
        Mixins.call(debug, "impetus$leftFromCache", cachedLeft);
        assertEquals(List.of("fps"), cachedLeft.getReturnValue());
        CallbackInfoReturnable<List<String>> cachedRight = Mixins.cir();
        Mixins.call(debug, "impetus$rightFromCache", cachedRight);
        assertEquals(List.of("java"), cachedRight.getReturnValue());
        Mixins.call(debug, "impetus$cacheLeft", cachedLeft);
        Mixins.call(debug, "impetus$cacheRight", cachedRight);
        assertEquals(List.of("fps"), Mixins.get(debug, "impetus$leftCache"));
    }

    @Test
    void worldLoadingSkipsItsHitches() {
        MinecraftLoadWorldGcMixin minecraft = Mixins.instance(MinecraftLoadWorldGcMixin.class);
        assertFalse((boolean) Mixins.<Boolean>call(minecraft, "impetus$skipForcedGc"));
        config.loading.skipWorldLoadGc = false;
        assertTrue((boolean) Mixins.<Boolean>call(minecraft, "impetus$skipForcedGc"));
        Mixins.call(minecraft, "impetus$releaseHudCache", null, "leaving", Mixins.ci());
        Mixins.call(minecraft, "impetus$releaseHudCache", mock(WorldClient.class), "joining", Mixins.ci());

        NetHandlerPlayClientRespawnMixin respawn = Mixins.instance(NetHandlerPlayClientRespawnMixin.class);
        Mc.Recorded<Void> display = Mc.operation();
        GuiDownloadTerrain terrain = Mc.uninitialized(GuiDownloadTerrain.class);
        Mixins.call(respawn, "impetus$skipDownloadTerrainScreen", client, terrain, display);
        assertNull(display.last()[1]);
        GuiScreen other = mock(GuiScreen.class);
        Mixins.call(respawn, "impetus$skipDownloadTerrainScreen", client, other, display);
        assertSame(other, display.last()[1]);
        config.loading.smoothDimensionChange = false;
        Mixins.call(respawn, "impetus$skipDownloadTerrainScreen", client, terrain, display);
        assertSame(terrain, display.last()[1]);
    }

    @Test
    void screenshotsAreWrittenOffTheRenderThread() throws Exception {
        assertNotNull(Mixins.instance(ScreenShotHelperMixin.class));
        Mixins.set(ScreenShotHelperMixin.class, "LOGGER", LogManager.getLogger("test"));
        // The readback buffers are dropped after each capture
        Mixins.set(ScreenShotHelperMixin.class, "pixelValues", new int[4]);
        Mixins.call(ScreenShotHelperMixin.class, "impetus$releaseReadbackBuffers", 1, 1, null, Mixins.cir());
        assertNull(Mixins.get(ScreenShotHelperMixin.class, "pixelValues"));
        config.loading.releaseScreenshotBuffers = false;
        Mixins.set(ScreenShotHelperMixin.class, "pixelValues", new int[4]);
        Mixins.call(ScreenShotHelperMixin.class, "impetus$releaseReadbackBuffers", 1, 1, null, Mixins.cir());
        assertNotNull(Mixins.get(ScreenShotHelperMixin.class, "pixelValues"));
        // The placeholder bodies are what Mixin replaces with vanilla's private methods
        assertThrows(AssertionError.class, () -> Mixins.call(ScreenShotHelperMixin.class, "createScreenshot", 1, 1, null));
        assertThrows(AssertionError.class, () -> Mixins.call(ScreenShotHelperMixin.class, "getTimestampedPNGFileForDirectory", dir.toFile()));

        ShadowStubs.on(null, "createScreenshot", args -> new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
        ShadowStubs.on(null, "getTimestampedPNGFileForDirectory", args -> new File((File) args[0], "shot.png"));
        config.loading.asyncScreenshots = true;
        CallbackInfoReturnable<ITextComponent> timestamped = Mixins.cir();
        Mixins.call(ScreenShotHelperMixin.class, "impetus$saveAsync", dir.toFile(), null, 2, 2, null, timestamped);
        assertEquals("screenshot.success", ((net.minecraft.util.text.TextComponentTranslation) timestamped.getReturnValue()).getKey());
        CallbackInfoReturnable<ITextComponent> named = Mixins.cir();
        Mixins.call(ScreenShotHelperMixin.class, "impetus$saveAsync", dir.toFile(), "named.png", 2, 2, null, named);
        assertTrue(named.isCancelled());
        awaitWritten(dir.resolve("screenshots/shot.png"));
        awaitWritten(dir.resolve("screenshots/named.png"));
        // A file that cannot be written is reported in chat from the client thread; ImageIO deletes an empty directory in its way, so this one has something in it
        Files.createDirectories(dir.resolve("screenshots/blocked.png"));
        Files.writeString(dir.resolve("screenshots/blocked.png/keep"), "");
        GuiIngame ingame = mock(GuiIngame.class);
        GuiNewChat chat = mock(GuiNewChat.class);
        when(ingame.getChatGUI()).thenReturn(chat);
        Mixins.set(client, "ingameGUI", ingame);
        java.util.concurrent.CountDownLatch reported = new java.util.concurrent.CountDownLatch(1);
        when(client.addScheduledTask(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            reported.countDown();
            return null;
        });
        Mixins.call(ScreenShotHelperMixin.class, "impetus$saveAsync", dir.toFile(), "blocked.png", 2, 2, null, Mixins.cir());
        assertTrue(reported.await(10, java.util.concurrent.TimeUnit.SECONDS));
        verify(chat).printChatMessage(any());
        // A capture that fails is reported straight away
        ShadowStubs.on(null, "createScreenshot", args -> {
            throw new IllegalStateException("no framebuffer");
        });
        CallbackInfoReturnable<ITextComponent> failed = Mixins.cir();
        Mixins.call(ScreenShotHelperMixin.class, "impetus$saveAsync", dir.toFile(), null, 2, 2, null, failed);
        assertEquals("screenshot.failure", ((net.minecraft.util.text.TextComponentTranslation) failed.getReturnValue()).getKey());
        // Switched off, vanilla saves as it always did
        config.loading.asyncScreenshots = false;
        CallbackInfoReturnable<ITextComponent> vanilla = Mixins.cir();
        Mixins.call(ScreenShotHelperMixin.class, "impetus$saveAsync", dir.toFile(), null, 2, 2, null, vanilla);
        assertFalse(vanilla.isCancelled());
    }

    // The writer thread must finish before the temporary directory is torn down
    private static void awaitWritten(Path file) throws InterruptedException {
        for (int i = 0; i < 200 && (!Files.exists(file) || file.toFile().length() == 0); i++) {
            Thread.sleep(50);
        }
        assertTrue(file.toFile().length() > 0, file.toString());
    }
}
