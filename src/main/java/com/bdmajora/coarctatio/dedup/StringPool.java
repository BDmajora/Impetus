package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;

// Long-lived string pools in the spirit of LoliASM's LoliStringPool, split per workload so chatty NBT cannot starve resource paths; String.intern cannot be sized or dropped
public final class StringPool {
    // NBT keys are extremely repetitive (id, Count, Damage, tag, x/y/z); sharded because packet decode, chunk IO and client threads intern concurrently
    public static final ShardedStringCache NBT_KEYS =
            new ShardedStringCache("NBT keys", CoarctatioConfig.get().poolSizeLimit);

    // Class, package and annotation names Forge's discovery scan produces, tens of thousands of them alive for the session; separate from NBT so a chatty save cannot evict them
    public static final ShardedStringCache LOADER =
            new ShardedStringCache("Loader names", CoarctatioConfig.get().poolSizeLimit);

    // Static-only
    private StringPool() {
    }
}
