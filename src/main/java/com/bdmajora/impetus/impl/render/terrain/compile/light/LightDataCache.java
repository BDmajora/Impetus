package com.bdmajora.impetus.impl.render.terrain.compile.light;

import com.bdmajora.fulgor.FulgorRenderBridge;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.impl.util.EmptyBlockAccess;

// Light data cache over a block access.
public class LightDataCache extends LightDataAccess {
    // Block access read for light.
    private final IBlockAccess world;
    // Reusable position.
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    // Creates a cache over a block access.
    public LightDataCache(IBlockAccess world) {
        this.world = world;
    }

    // Returns the packed light data of a block.
    protected int compute(int x, int y, int z) {
        BlockPos pos = this.pos.setPos(x, y, z);
        IBlockAccess world = this.world;

        var state = world.getBlockState(pos);

        boolean em;

        try {
            em = state.getPackedLightmapCoords(EmptyBlockAccess.INSTANCE, BlockPos.ORIGIN) == 15728880;
        } catch (Exception e) {
            em = false;
        }

        boolean op = !state.isTranslucent() && state.getLightOpacity(world, pos) == 0;
        boolean fo = state.isOpaqueCube();
        boolean fc = state.isBlockNormalCube();

        int lu = state.getLightValue(world, pos);

        int bl;
        int sl;
        if (fo && lu == 0) {
            bl = 0;
            sl = 0;
        } else {
            int packedCoords = state.getPackedLightmapCoords(world, pos);
            bl = LightDataAccess.unpackBlock(packedCoords);
            sl = LightDataAccess.unpackSky(packedCoords);
        }

        float ao;
        if (lu == 0 || (lu == 1 && FulgorRenderBridge.fixRenderLighting())) {
            ao = com.bdmajora.impetus.umbra.material.WorldRenderingSettings
                    .applyAmbientOcclusionLevel(state.getAmbientOcclusionLightValue());
        } else {
            ao = 1.0f;
        }

        return packFC(fc) | packFO(fo) | packOP(op) | packEM(em) | packAO(ao) | packLU(lu) | packSL(sl) | packBL(bl);
    }
}
