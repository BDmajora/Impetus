package com.bdmajora.coarctatio.state;

import net.minecraft.block.Block;

// Block that owns a property value mapper.
public interface MappedStateOwner {
    // Returns the mapper of the block.
    PropertyValueMapper coarctatio$mapper(Block block);
}
