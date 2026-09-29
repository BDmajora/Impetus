package com.bdmajora.impetus.mixin.features;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.sprite.SpriteTransparencyLevel;
import com.bdmajora.impetus.engine.impl.texture.MipmapHelper;
import com.bdmajora.impetus.impl.extensions.SpriteExtension;
import com.bdmajora.impetus.impl.extensions.TextureMapExtension;
import com.bdmajora.impetus.impl.gui.ImpetusVideoOptionsScreen;
import com.bdmajora.impetus.mixin.features.mipmaps.TextureUtilMixin;
import com.bdmajora.impetus.mixin.features.options.MixinEntityRenderer;
import com.bdmajora.impetus.mixin.features.options.MixinGuiIngameForge;
import com.bdmajora.impetus.mixin.features.options.MixinGuiOptions;
import com.bdmajora.impetus.mixin.features.render.tileentity.TileEntityRenderDispatcherMixin;
import com.bdmajora.impetus.mixin.features.render.tileentity.piston.TileEntityPistonRendererMixin;
import com.bdmajora.impetus.mixin.features.textures.RenderItemMixin;
import com.bdmajora.impetus.mixin.features.textures.TextureAtlasMixin;
import com.bdmajora.impetus.umbra.pbr.PBRAtlasManager;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionsScreens;
import com.bdmajora.testing.Statics;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureMixinsTest {
    private Minecraft client;
    private ImpetusGameOptions previousOptions;
    private ImpetusGameOptions options;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
    }

    @BeforeEach
    void freshClient() throws ClassNotFoundException {
        client = Mc.client();
        Class.forName(ImpetusVintage.class.getName());
        previousOptions = Statics.get(ImpetusVintage.class, "CONFIG");
        options = ImpetusGameOptions.defaults();
        Statics.set(ImpetusVintage.class, "CONFIG", options);
    }

    @AfterEach
    void restore() {
        Statics.set(ImpetusVintage.class, "CONFIG", previousOptions);
    }

    // A sprite at a place in a 32x32 atlas
    private static TextureAtlasSprite placed(String name, int x, int y, int width, int height) {
        TextureAtlasSprite sprite = new TextureAtlasSprite(name) {
        };
        sprite.setIconWidth(width);
        sprite.setIconHeight(height);
        sprite.initSprite(32, 32, x, y, false);
        return sprite;
    }

    @Test
    void itemQuadsMarkTheirSpritesDrawn() {
        RenderItemMixin items = Mixins.instance(RenderItemMixin.class);
        TextureAtlasSprite sprite = Mc.mock(TextureAtlasSprite.class, SpriteExtension.class);
        BakedQuad quad = new BakedQuad(new int[28], -1, EnumFacing.UP, sprite, true, DefaultVertexFormats.ITEM);
        assertSame(quad, Mixins.call(items, "markSpriteActive", quad));
        verify((SpriteExtension) sprite).impetus$markActive();
        BakedQuad bare = new BakedQuad(new int[28], -1, EnumFacing.UP, null, true, DefaultVertexFormats.ITEM);
        assertSame(bare, Mixins.call(items, "markSpriteActive", bare));
        assertEquals("not a quad", Mixins.call(items, "markSpriteActive", "not a quad"));
    }

    @Test
    void theAtlasIndexesItsSpritesByUv() {
        TextureAtlasMixin atlas = Mixins.instance(TextureAtlasMixin.class);
        TextureAtlasSprite first = placed("first", 0, 0, 16, 16);
        TextureAtlasSprite second = placed("second", 16, 0, 16, 16);
        TextureAtlasSprite wide = placed("wide", 0, 16, 32, 16);
        Map<String, TextureAtlasSprite> uploaded = new LinkedHashMap<>();
        uploaded.put("first", first);
        uploaded.put("second", second);
        uploaded.put("wide", wide);
        Mixins.set(atlas, "mapUploadedSprites", uploaded);
        Mixins.set(atlas, "mipmapLevels", 4);
        // No GL texture yet, so the filtering step has nothing to bind
        doReturn(0).when((AbstractTexture) (Object) atlas).getGlTextureId();
        Stitcher stitcher = mock(Stitcher.class);
        when(stitcher.getCurrentWidth()).thenReturn(32);
        when(stitcher.getCurrentHeight()).thenReturn(32);
        try (MockedStatic<PBRAtlasManager> pbr = Mockito.mockStatic(PBRAtlasManager.class)) {
            Mixins.call(atlas, "generateQuadTree", Mixins.ci(), stitcher);
            pbr.verify(() -> PBRAtlasManager.rebuild(uploaded, 32, 32, 4));
        }
        TextureMapExtension index = atlas;
        assertNotNull(index.impetus$getQuadTree());
        assertSame(first, index.impetus$findFromUV(0.1F, 0.1F));
        assertSame(second, index.impetus$findFromUV(0.75F, 0.25F));
        assertSame(wide, index.impetus$findFromUV(0.5F, 0.75F));

        // Only sprites something drew since the last update animate, unless every sprite is asked to
        com.bdmajora.impetus.mixin.features.textures.TextureAtlasSpriteMixin drawn =
                Mixins.instance(com.bdmajora.impetus.mixin.features.textures.TextureAtlasSpriteMixin.class);
        com.bdmajora.impetus.mixin.features.textures.TextureAtlasSpriteMixin hidden =
                Mixins.instance(com.bdmajora.impetus.mixin.features.textures.TextureAtlasSpriteMixin.class);
        drawn.impetus$markActive();
        List<TextureAtlasSprite> sprites = List.of((TextureAtlasSprite) (Object) drawn, (TextureAtlasSprite) (Object) hidden);
        Iterator<TextureAtlasSprite> filtered = Mixins.call(atlas, "getFilteredIterator", sprites.iterator());
        assertEquals(List.of(sprites.get(0)), Lists.newArrayList(filtered));
        options.performance.animateOnlyVisibleTextures = false;
        Iterator<TextureAtlasSprite> all = sprites.iterator();
        assertSame(all, Mixins.call(atlas, "getFilteredIterator", all));
    }

    @Test
    void aSpriteIsActiveOnceUntilItIsAskedAbout() {
        com.bdmajora.impetus.mixin.features.textures.TextureAtlasSpriteMixin sprite =
                Mixins.instance(com.bdmajora.impetus.mixin.features.textures.TextureAtlasSpriteMixin.class);
        assertFalse(sprite.impetus$shouldUpdate());
        // Reading a UV counts as being drawn
        assertEquals(0.5F, (float) Mixins.call(sprite, "markActiveWhenGettingCoords", 0.5F));
        assertTrue(sprite.impetus$shouldUpdate());
        assertFalse(sprite.impetus$shouldUpdate());
    }

    @Test
    void weatherAndVignetteFollowTheImpetusOptions() {
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        Mixins.set(client, "gameSettings", settings);
        MixinGuiIngameForge hud = Mixins.instance(MixinGuiIngameForge.class);
        options.quality.enableVignette = false;
        assertFalse((boolean) Mixins.call(hud, "impetus$redirectVignette"));
        options.quality.enableVignette = true;
        assertTrue((boolean) Mixins.call(hud, "impetus$redirectVignette"));

        MixinEntityRenderer weather = Mixins.instance(MixinEntityRenderer.class);
        // Default weather quality follows vanilla's fancy toggle, the others override it
        settings.fancyGraphics = true;
        assertTrue((boolean) Mixins.call(weather, "redirectGetFancyWeather", settings));
        options.quality.weatherQuality = ImpetusGameOptions.GraphicsQuality.FAST;
        assertFalse((boolean) Mixins.call(weather, "redirectGetFancyWeather", settings));
        options.quality.weatherQuality = ImpetusGameOptions.GraphicsQuality.FANCY;
        settings.fancyGraphics = false;
        assertTrue((boolean) Mixins.call(weather, "redirectGetFancyWeather", settings));
        options.quality.weatherEffectRadius = 8;
        assertEquals(4, (int) Mixins.call(weather, "useConfiguredFastWeatherRadius", 5));
        assertEquals(8, (int) Mixins.call(weather, "useConfiguredFancyWeatherRadius", 10));
        assertEquals(8, (int) Mixins.call(weather, "useConfiguredRainParticleRadius", 10));
        options.quality.weatherEffectRadius = 1;
        assertEquals(1, (int) Mixins.call(weather, "useConfiguredFastWeatherRadius", 5));
        options.quality.weatherEffectRadius = 0;
        assertEquals(1, (int) Mixins.call(weather, "useConfiguredFancyWeatherRadius", 10));
        assertEquals(1, (int) Mixins.call(weather, "useConfiguredRainParticleRadius", 10));
    }

    @Test
    void theVideoSettingsButtonOpensTheImpetusScreen(@TempDir Path home) {
        Mc.forge();
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        settings.renderDistanceChunks = 8;
        settings.limitFramerate = 120;
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 854;
        client.displayHeight = 480;
        OptionsScreens.prepare(client, home);
        try {
            MixinGuiOptions screen = Mixins.instance(MixinGuiOptions.class);
            Mixins.set(screen, "mc", client);
            // Other buttons, and a disabled video button, are left to vanilla
            CallbackInfo other = Mixins.ci();
            Mixins.call(screen, "open", new GuiButton(100, 0, 0, "Controls"), other);
            assertFalse(other.isCancelled());
            GuiButton disabled = new GuiButton(101, 0, 0, "Video");
            disabled.enabled = false;
            CallbackInfo off = Mixins.ci();
            Mixins.call(screen, "open", disabled, off);
            assertFalse(off.isCancelled());
            verify(client, never()).displayGuiScreen(any());
            CallbackInfo video = Mixins.ci();
            Mixins.call(screen, "open", new GuiButton(101, 0, 0, "Video"), video);
            assertTrue(video.isCancelled());
            verify(client).displayGuiScreen(any(ImpetusVideoOptionsScreen.class));
        } finally {
            OptionsScreens.forget();
        }
    }

    @Test
    void blockTexturesAreClassifiedAndTheirClearPixelsRecoloured() {
        int[] a = {0xFF000000, 0xFFFFFFFF, 0x80FF0000, 0x00000000};
        assertEquals(MipmapHelper.weightedAverageColor(MipmapHelper.weightedAverageColor(a[0], a[1]), MipmapHelper.weightedAverageColor(a[2], a[3])),
                (int) Mixins.call(TextureUtilMixin.class, "blendColors", a[0], a[1], a[2], a[3], true));
        assertNotNull(Mixins.instance(TextureUtilMixin.class));

        com.bdmajora.impetus.mixin.features.mipmaps.TextureAtlasSpriteMixin sprite =
                Mixins.instance(com.bdmajora.impetus.mixin.features.mipmaps.TextureAtlasSpriteMixin.class);
        // Nothing loaded yet: the sprite stays translucent, the safe assumption
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(SpriteTransparencyLevel.TRANSLUCENT, sprite.impetus$getTransparencyLevel());

        // An opaque block texture with fully clear pixels is transparent, and at a mip level its clear pixels take the average colour
        int[] glass = {0xFF204060, 0xFF204060, 0x00000000, 0x00FFFFFF};
        List<int[][]> frames = new ArrayList<>();
        frames.add(new int[][] {glass});
        frames.add(null);
        frames.add(new int[][] {null});
        Mixins.set(sprite, "framesTextureData", frames);
        Mixins.set(sprite, "iconName", "minecraft:blocks/glass");
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(SpriteTransparencyLevel.TRANSPARENT, sprite.impetus$getTransparencyLevel());
        // The average survives the round trip through linear space to within a step per channel
        assertEquals(0, glass[2] >>> 24);
        assertEquals(0x20, (glass[2] >> 16) & 255, 1);
        assertEquals(0x40, (glass[2] >> 8) & 255, 1);
        assertEquals(0x60, glass[2] & 255, 1);
        assertEquals(glass[2], glass[3]);

        // Partial alpha makes it translucent; without mipmaps, or for items and leaves, colours are left alone
        int[] item = {0x80FF0000, 0x00123456};
        Mixins.set(sprite, "framesTextureData", List.<int[][]>of(new int[][] {item}));
        Mixins.set(sprite, "iconName", "minecraft:items/apple");
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(SpriteTransparencyLevel.TRANSLUCENT, sprite.impetus$getTransparencyLevel());
        assertEquals(0x00123456, item[1]);
        Mixins.set(sprite, "iconName", "minecraft:blocks/leaves_oak");
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(0x00123456, item[1]);
        Mixins.set(sprite, "iconName", "minecraft:blocks/stone");
        Mixins.call(sprite, "processSprite", 0, Mixins.ci());
        assertEquals(0x00123456, item[1]);
        Mixins.set(sprite, "iconName", null);
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(0x00123456, item[1]);
        // A block texture with no visible pixel at all has nothing to average
        int[] clear = {0x00000000, 0x00ABCDEF};
        Mixins.set(sprite, "framesTextureData", List.<int[][]>of(new int[][] {clear}));
        Mixins.set(sprite, "iconName", "stone_block");
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        Mixins.set(sprite, "iconName", "minecraft:blocks/air");
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(SpriteTransparencyLevel.TRANSPARENT, sprite.impetus$getTransparencyLevel());
        assertEquals(0x00ABCDEF, clear[1]);
        // A fully opaque texture is opaque
        Mixins.set(sprite, "framesTextureData", List.<int[][]>of(new int[][] {{0xFF000000}}));
        Mixins.call(sprite, "processSprite", 4, Mixins.ci());
        assertEquals(SpriteTransparencyLevel.OPAQUE, sprite.impetus$getTransparencyLevel());
    }

    @Test
    void pistonsKeepDrawingWhileTheirSectionCatchesUp() {
        TileEntityRenderDispatcherMixin dispatcher = Mixins.instance(TileEntityRenderDispatcherMixin.class);
        Mc.Recorded<Boolean> invalid = Mc.operation(true);
        assertFalse((boolean) Mixins.call(dispatcher, "allowSomeInvalidTESRs", new TileEntityPiston(), invalid));
        assertTrue(invalid.calls.isEmpty());
        TileEntity chest = new TileEntityChest();
        assertTrue((boolean) Mixins.call(dispatcher, "allowSomeInvalidTESRs", chest, invalid));
        assertSame(chest, invalid.calls.get(0)[0]);

        TileEntityPistonRendererMixin renderer = Mixins.instance(TileEntityPistonRendererMixin.class);
        assertEquals(0F, (float) Mixins.call(renderer, "alwaysRenderTESR", new TileEntityPiston(), 0.75F));
    }
}
