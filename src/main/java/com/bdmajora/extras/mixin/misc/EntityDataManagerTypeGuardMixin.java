package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.Extras;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Refuses synced entity data whose type does not match the slot it lands in, like modern SynchedEntityData does.
// 1.12 copies values by id with no type check, so a clashing id (a mob's AI_FLAGS byte on the player's ABSORPTION float, slot 11)
// sits there until a getter casts it and the HUD crashes; dropping the write keeps the slot's own value and logs the source once.
@Mixin(EntityDataManager.class)
public abstract class EntityDataManagerTypeGuardMixin {
    // Slots already reported, keyed by entity class and slot id, so a repeating clash logs once.
    @Unique
    private static final Set<String> impetus$reported = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private Map<Integer, EntityDataManager.DataEntry<?>> entries;

    // A local set with a key from another class's id range: cancel before the value is stored, stack trace names the caller.
    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private <T> void impetus$refuseMismatchedSet(DataParameter<T> key, T value, CallbackInfo ci) {
        EntityDataManager.DataEntry<?> existing = this.entries.get(key.getId());
        if (existing != null && existing.getKey().getSerializer() != key.getSerializer()) {
            impetus$report(existing, key, value, true);
            ci.cancel();
        }
    }

    // A synced batch: drop only the entries whose serializer differs from the stored slot; the list is copied only when one does.
    @ModifyVariable(method = "setEntryValues", at = @At("HEAD"), argsOnly = true)
    private List<EntityDataManager.DataEntry<?>> impetus$dropMismatchedEntries(List<EntityDataManager.DataEntry<?>> incoming) {
        List<EntityDataManager.DataEntry<?>> kept = null;
        for (int i = 0, n = incoming.size(); i < n; i++) {
            EntityDataManager.DataEntry<?> entry = incoming.get(i);
            EntityDataManager.DataEntry<?> existing = this.entries.get(entry.getKey().getId());
            boolean clash = existing != null && existing.getKey().getSerializer() != entry.getKey().getSerializer();
            if (clash && kept == null) {
                kept = new ArrayList<>(incoming.subList(0, i));
            }
            if (clash) {
                impetus$report(existing, entry.getKey(), entry.getValue(), false);
            } else if (kept != null) {
                kept.add(entry);
            }
        }
        return kept == null ? incoming : kept;
    }

    // Logs one warning per entity class and slot with both value types; a local set adds the stack so the calling mod shows.
    @Unique
    private void impetus$report(EntityDataManager.DataEntry<?> existing, DataParameter<?> key, Object value, boolean localSet) {
        String owner = this.entity == null ? "null" : this.entity.getClass().getName();
        if (!impetus$reported.add(owner + '#' + key.getId())) {
            return;
        }
        Object current = existing.getValue();
        String message = "Dropped a {} for data slot {} on {} ({}): the slot holds {} but the value is {}. Another mod's data key shares this id";
        Object[] args = {localSet ? "write" : "synced value", key.getId(), owner, this.entity,
                current == null ? "null" : current.getClass().getSimpleName(), value == null ? "null" : value.getClass().getSimpleName()};
        if (localSet) {
            Object[] withStack = Arrays.copyOf(args, args.length + 1);
            withStack[args.length] = new Throwable("data key caller");
            Extras.LOGGER.warn(message, withStack);
        } else {
            Extras.LOGGER.warn(message + " or the entity ids clash", args);
        }
    }
}
