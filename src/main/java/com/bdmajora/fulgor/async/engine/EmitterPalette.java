package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.mixin.async.world.BlockStateContainerPaletteAccessor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.BlockStatePaletteHashMap;
import net.minecraft.world.chunk.BlockStatePaletteLinear;
import net.minecraft.world.chunk.IBlockStatePalette;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Palette test for whether a section can hold a light source, the way Starlight skips sections before scanning them.
final class EmitterPalette {
    // Ids a local palette can hand out: eight bits at most before the section switches to the global registry.
    static final int LOCAL_PALETTE_IDS = 1 << 8;

    // Prevents instantiation
    private EmitterPalette() {
    }

    // Returns whether any state the section's palette lists may emit light; true whenever the palette cannot tell.
    static boolean mayEmitLight(ExtendedBlockStorage section) {
        BlockStateContainer data = section.getData();
        if (!(data instanceof BlockStateContainerPaletteAccessor accessor)) {
            return true;
        }
        IBlockStatePalette palette = accessor.fulgor$getPalette();
        // The global registry palette lists every state in the game, and a mod's own palette is unknown ground
        if (!(palette instanceof BlockStatePaletteLinear) && !(palette instanceof BlockStatePaletteHashMap)) {
            return true;
        }
        // Palettes never shrink, so a state that has since been replaced only costs a scan, never a missed light
        for (int id = 0; id < LOCAL_PALETTE_IDS; id++) {
            IBlockState state = palette.getBlockState(id);
            if (state != null && mayEmit(LightInfo.of(state))) {
                return true;
            }
        }
        return false;
    }

    // Returns whether a state's info emits light or asks the world what it emits.
    static boolean mayEmit(int info) {
        return LightInfo.emission(info) > 0 || (info & LightInfo.CONTEXT_EMISSION) != 0;
    }
}
