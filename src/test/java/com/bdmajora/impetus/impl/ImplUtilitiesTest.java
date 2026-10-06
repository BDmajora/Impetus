package com.bdmajora.impetus.impl;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderPassConfiguration;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkMeshFormats;
import com.bdmajora.impetus.impl.command.TogglePassCommand;
import com.bdmajora.impetus.impl.render.terrain.ImpetusWorldRenderer;
import com.bdmajora.impetus.impl.render.terrain.VintageRenderPassConfigurationBuilder;
import com.bdmajora.impetus.impl.render.terrain.VintageRenderSectionManager;
import com.bdmajora.impetus.impl.render.texture.BlockAtlasFiltering;
import com.bdmajora.impetus.impl.util.EmptyBlockAccess;
import com.bdmajora.impetus.impl.util.PlatformUtil;
import com.bdmajora.impetus.impl.util.StringUtils;
import com.bdmajora.impetus.mixin.core.terrain.RenderGlobalMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestCaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImplUtilitiesTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @AfterEach
    void restore() {
        Mixins.set(BlockAtlasFiltering.class, "anisotropySupported", null);
        ImpetusRuntimeOptions.pixelFiltering = ImpetusGameOptions.PixelFilteringMode.NEAREST;
    }

    @Test
    void optionSearchMatchesEveryWordByPrefixOrNearMiss() {
        new StringUtils();
        assertEquals(3, StringUtils.levenshteinDistance("kitten", "sitting"));
        assertEquals(0, StringUtils.levenshteinDistance("", ""));
        assertEquals(4, StringUtils.levenshteinDistance("", "fogs"));
        List<String> options = List.of("Render Distance", "Smooth Lighting", "Entity Distance");
        assertEquals(List.of("Render Distance", "Entity Distance"), StringUtils.fuzzySearch(options, "dist", 1, Function.identity()));
        assertEquals(List.of("Render Distance"), StringUtils.fuzzySearch(options, "rendr  DIST", 1, Function.identity()));
        assertEquals(List.of(), StringUtils.fuzzySearch(options, "clouds", 1, Function.identity()));
    }

    @Test
    void theEmptyWorldIsAirEverywhere() {
        EmptyBlockAccess empty = EmptyBlockAccess.INSTANCE;
        BlockPos pos = new BlockPos(1, 2, 3);
        assertNull(empty.getTileEntity(pos));
        assertEquals(0, empty.getCombinedLight(pos, 15));
        assertSame(Blocks.AIR.getDefaultState(), empty.getBlockState(pos));
        assertTrue(empty.isAirBlock(pos));
        assertSame(Biomes.PLAINS, empty.getBiome(pos));
        assertEquals(0, empty.getStrongPower(pos, EnumFacing.UP));
        assertSame(WorldType.DEFAULT, empty.getWorldType());
        assertFalse(empty.isSideSolid(pos, EnumFacing.UP, true));
    }

    @Test
    void theGameDirectoryIsTheClients() {
        new PlatformUtil();
        Minecraft client = Mc.client();
        File dir = new File("game");
        Mixins.set(client, "gameDir", dir);
        assertSame(dir, PlatformUtil.getGameDir());
    }

    @Test
    void theDebugCommandTogglesANamedPass() throws ClassNotFoundException {
        Class.forName(ImpetusVintage.class.getName());
        Minecraft client = Mc.client();
        RenderGlobalMixin global = Mixins.instance(RenderGlobalMixin.class);
        Mixins.set(client, "renderGlobal", global);
        TogglePassCommand command = new TogglePassCommand();
        ICommandSender sender = mock(ICommandSender.class);
        assertEquals(0, command.getRequiredPermissionLevel());
        assertEquals("impetus_toggle_pass", command.getName());
        assertEquals("/impetus_toggle_pass [pass_name]", command.getUsage(sender));
        // Before a world loads there are no passes to name
        assertEquals(List.of(), command.getTabCompletions(null, sender, new String[0], null));
        command.execute(null, sender, new String[] {"solid"});

        ImpetusWorldRenderer renderer = mock(ImpetusWorldRenderer.class);
        RenderPassConfiguration<BlockRenderLayer> passes = VintageRenderPassConfigurationBuilder.build(ChunkMeshFormats.COMPACT);
        doReturn(passes).when(renderer).getRenderPassConfiguration();
        VintageRenderSectionManager manager = mock(VintageRenderSectionManager.class);
        when(renderer.getRenderSectionManager()).thenReturn(manager);
        Mixins.set(global, "renderer", renderer);
        assertEquals(List.of("solid", "cutout_mipped", "translucent", "cutout"), command.getTabCompletions(null, sender, new String[0], null));
        command.execute(null, sender, new String[] {"translucent"});
        TerrainRenderPass translucent = passes.getAllKnownRenderPasses().filter(pass -> pass.name().equals("translucent")).findFirst().orElseThrow();
        verify(manager).toggleRenderingForTerrainPass(translucent);

        command.execute(null, sender, new String[0]);
        command.execute(null, sender, new String[] {"nope"});
        ArgumentCaptor<ITextComponent> messages = ArgumentCaptor.forClass(ITextComponent.class);
        verify(sender, Mockito.times(3)).sendMessage(messages.capture());
        assertEquals(List.of("Pass solid not found", "Pass name must be provided", "Pass nope not found"),
                messages.getAllValues().stream().map(ITextComponent::getUnformattedText).toList());
    }

    @Test
    void theAtlasIsFilteredNearestWithAnisotropyReset() {
        Mixins.set(BlockAtlasFiltering.class, "anisotropySupported", null);
        Statics.set(Minecraft.class, "instance", null);
        BlockAtlasFiltering.reapplyToBlockAtlas();
        Minecraft client = Mc.client();
        GameSettings settings = Mc.uninitialized(GameSettings.class);
        settings.mipmapLevels = 4;
        Mixins.set(client, "gameSettings", settings);
        BlockAtlasFiltering.reapplyToBlockAtlas();
        TextureMap atlas = mock(TextureMap.class);
        when(atlas.getGlTextureId()).thenReturn(7);
        when(client.getTextureMapBlocks()).thenReturn(atlas);
        try (MockedStatic<GL11> gl11 = Mockito.mockStatic(GL11.class);
             MockedStatic<GL> gl = TestCaps.install(TestCaps.all())) {
            BlockAtlasFiltering.reapplyToBlockAtlas();
            gl11.verify(() -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, 7));
            gl11.verify(() -> GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR));
            gl11.verify(() -> GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST));
            gl11.verify(() -> GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f));

            // Without mipmaps minification is plain nearest, and linear magnification follows the option
            settings.mipmapLevels = 0;
            ImpetusRuntimeOptions.pixelFiltering = ImpetusGameOptions.PixelFilteringMode.LINEAR;
            BlockAtlasFiltering.apply(8);
            gl11.verify(() -> GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST));
            gl11.verify(() -> GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR));
            // No texture, nothing bound
            BlockAtlasFiltering.apply(0);
            gl11.verify(() -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0), never());

            // A driver without the extension, or no context at all, skips the anisotropy reset
            Mixins.set(BlockAtlasFiltering.class, "anisotropySupported", null);
            gl.when(GL::getCapabilities).thenReturn(TestCaps.with(TestCaps.all(), "GL_EXT_texture_filter_anisotropic", false));
            BlockAtlasFiltering.apply(9);
            Mixins.set(BlockAtlasFiltering.class, "anisotropySupported", null);
            gl.when(GL::getCapabilities).thenThrow(new IllegalStateException("no context"));
            BlockAtlasFiltering.apply(10);
            gl11.verify(() -> GL11.glTexParameterf(anyInt(), anyInt(), anyFloat()), Mockito.times(2));
        }
    }
}
