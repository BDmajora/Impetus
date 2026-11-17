package com.bdmajora.coarctatio.mixin.core;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.LockCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoreMixinsTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void aRegistryIsBackedByACompactMap() {
        // Builds a concrete registry.
        @SuppressWarnings("unchecked")
        RegistrySimpleMixin<String, String> registry = Mixins.concrete(RegistrySimpleMixin.class);
        CallbackInfoReturnable<Map<String, String>> cir = Mixins.cir();
        Mixins.call(registry, "coarctatio$compactBackingMap", cir);
        // Checks the head cancel.
        assertTrue(cir.isCancelled());
        assertInstanceOf(Object2ObjectOpenHashMap.class, cir.getReturnValue());
    }

    @Test
    void anEntitysSyncedDataMapDropsItsBoxing() {
        EntityDataManagerMixin manager = Mixins.instance(EntityDataManagerMixin.class);
        Mixins.set(manager, "entries", new HashMap<>());
        Mixins.call(manager, "coarctatio$compactEntries", Mixins.ci());
        assertInstanceOf(Int2ObjectOpenHashMap.class, Mixins.get(manager, "entries"));
    }

    @Test
    void aSectionsEntityLookupIsCopiedIntoReferenceKeyedCollections() {
        @SuppressWarnings("unchecked")
        ClassInheritanceMultiMapMixin<Object> lookup = Mixins.concrete(ClassInheritanceMultiMapMixin.class);
        Map<Class<?>, List<Object>> map = new HashMap<>();
        map.put(String.class, new ArrayList<>(List.of("a")));
        Set<Class<?>> known = new HashSet<>(Set.of(String.class));
        Mixins.set(lookup, "map", map);
        Mixins.set(lookup, "knownKeys", known);

        Mixins.call(lookup, "coarctatio$compactLookups", Mixins.ci());
        Map<Class<?>, List<Object>> compacted = Mixins.get(lookup, "map");
        assertInstanceOf(Reference2ObjectOpenHashMap.class, compacted);
        // Checks the contents come across.
        assertEquals(List.of("a"), compacted.get(String.class));
        Set<Class<?>> keys = Mixins.get(lookup, "knownKeys");
        assertInstanceOf(ReferenceOpenHashSet.class, keys);
        assertTrue(keys.contains(String.class));
    }

    @Test
    void anEmptyLockIsSharedRatherThanAllocated() {
        LockCodeMixin lock = Mixins.concrete(LockCodeMixin.class);
        // Fills the shadowed static.
        com.bdmajora.testing.Statics.set(LockCodeMixin.class, "EMPTY_CODE", LockCode.EMPTY_CODE);
        NBTTagCompound empty = new NBTTagCompound();
        empty.setString("Lock", "");
        CallbackInfoReturnable<LockCode> cir = Mixins.cir();
        Mixins.call(LockCodeMixin.class, "coarctatio$shareEmptyLock", empty, cir);
        assertTrue(cir.isCancelled());
        assertSame(LockCode.EMPTY_CODE, cir.getReturnValue());

        // Checks a real lock.
        NBTTagCompound named = new NBTTagCompound();
        named.setString("Lock", "key");
        CallbackInfoReturnable<LockCode> real = Mixins.cir();
        Mixins.call(LockCodeMixin.class, "coarctatio$shareEmptyLock", named, real);
        assertFalse(real.isCancelled());

        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setInteger("Lock", 0);
        CallbackInfoReturnable<LockCode> odd = Mixins.cir();
        Mixins.call(LockCodeMixin.class, "coarctatio$shareEmptyLock", wrongType, odd);
        assertFalse(odd.isCancelled());

        CallbackInfoReturnable<LockCode> absent = Mixins.cir();
        Mixins.call(LockCodeMixin.class, "coarctatio$shareEmptyLock", new NBTTagCompound(), absent);
        assertFalse(absent.isCancelled());
        assertNotNull(lock);
    }

    @Test
    void theObjectHolderListIsTrimmedNotCleared() {
        ObjectHolderRegistryMixin registry = Mixins.instance(ObjectHolderRegistryMixin.class);
        ArrayList<Object> holders = new ArrayList<>(64);
        holders.add("holder");
        Mixins.set(registry, "objectHolders", holders);
        Mixins.call(registry, "coarctatio$trimHolders", Mixins.ci());
        // Checks the refs survive.
        assertEquals(1, holders.size());

        // A list another mod replaced is left alone
        List<Object> foreign = new LinkedList<>();
        Mixins.set(registry, "objectHolders", foreign);
        Mixins.call(registry, "coarctatio$trimHolders", Mixins.ci());
        assertSame(foreign, Mixins.get(registry, "objectHolders"));
    }
}
