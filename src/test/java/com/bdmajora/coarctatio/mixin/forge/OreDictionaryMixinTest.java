package com.bdmajora.coarctatio.mixin.forge;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.ShadowStubs;
import com.bdmajora.testing.Statics;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OreDictionaryMixinTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void emptyRegistry() {
        Statics.set(OreDictionaryMixin.class, "nameToId", new HashMap<String, Integer>());
        Statics.set(OreDictionaryMixin.class, "stackToId", new HashMap<Integer, List<Integer>>());
        Statics.set(OreDictionaryMixin.class, "idToName", new ArrayList<String>());
        Statics.set(OreDictionaryMixin.class, "idToStack", new ArrayList<NonNullList<ItemStack>>());
        Statics.set(OreDictionaryMixin.class, "idToStackUn", new ArrayList<NonNullList<ItemStack>>());
        // The maps the class initialiser would have installed before any vanilla entry was registered
        Mixins.call(OreDictionaryMixin.class, "coarctatio$installPrimitiveMaps", Mixins.ci());
        ShadowStubs.on(null, "getOres", args -> {
            List<NonNullList<ItemStack>> byId = Statics.get(OreDictionaryMixin.class, "idToStack");
            return byId.get((Integer) args[0]);
        });
    }

    @AfterEach
    void forgetShadowAnswers() {
        ShadowStubs.clear();
    }

    @Test
    void anOreNameIsRegisteredOnceAndLooksUpWithoutBoxing() {
        assertFalse(OreDictionaryMixin.doesOreNameExist("ingotIron"));
        int id = OreDictionaryMixin.getOreID("ingotIron");
        assertEquals(0, id);
        // The same name always answers with the id it was given
        assertEquals(id, OreDictionaryMixin.getOreID("ingotIron"));
        assertTrue(OreDictionaryMixin.doesOreNameExist("ingotIron"));
        assertEquals(1, OreDictionaryMixin.getOreID("ingotGold"));

        List<String> names = Statics.get(OreDictionaryMixin.class, "idToName");
        assertEquals(Arrays.asList("ingotIron", "ingotGold"), names);
        List<NonNullList<ItemStack>> stacks = Statics.get(OreDictionaryMixin.class, "idToStack");
        assertEquals(2, stacks.size());
        // The mutable and unmodifiable views are the same list, as vanilla has them
        List<NonNullList<ItemStack>> unmodifiable = Statics.get(OreDictionaryMixin.class, "idToStackUn");
        assertSame(stacks.get(0), unmodifiable.get(0));
    }

    @Test
    void anUnknownNameIsNotGivenAnEntryUnlessItIsAskedFor() {
        assertSame(OreDictionary.EMPTY_LIST, OreDictionaryMixin.getOres("nothingLikeThis", false));
        assertTrue(Statics.<List<String>>get(OreDictionaryMixin.class, "idToName").isEmpty());

        NonNullList<ItemStack> created = OreDictionaryMixin.getOres("ingotIron", true);
        assertNotNull(created);
        assertEquals(1, Statics.<List<String>>get(OreDictionaryMixin.class, "idToName").size());
        // Now that it exists, the non-creating lookup finds it
        assertSame(created, OreDictionaryMixin.getOres("ingotIron", false));
    }

    @Test
    void theIdsOfAStackAreCollectedWithoutBoxing() {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        int itemId = Item.REGISTRY.getIDForObject(Items.IRON_INGOT);
        Int2ObjectOpenHashMap<IntList> stacks = Statics.get(OreDictionaryMixin.class, "stackToId");

        // Nothing registered for the item at all
        assertArrayEquals(new int[0], OreDictionaryMixin.getOreIDs(iron));

        // Registered against the item, then against this exact damage value as well
        stacks.put(itemId, new IntArrayList(new int[] {3}));
        assertArrayEquals(new int[] {3}, OreDictionaryMixin.getOreIDs(iron));
        stacks.put(itemId | ((iron.getItemDamage() + 1) << 16), new IntArrayList(new int[] {4}));
        int[] both = OreDictionaryMixin.getOreIDs(iron);
        Arrays.sort(both);
        assertArrayEquals(new int[] {3, 4}, both);

        // Only the damage-specific entry
        stacks.remove(itemId);
        assertArrayEquals(new int[] {4}, OreDictionaryMixin.getOreIDs(iron));

        // An empty stack is a caller bug
        assertThrows(IllegalArgumentException.class, () -> OreDictionaryMixin.getOreIDs(ItemStack.EMPTY));
    }

    @Test
    void aBoxedListAnotherModStoredIsConvertedOnFirstSight() {
        Int2ObjectOpenHashMap<Object> raw = Statics.get(OreDictionaryMixin.class, "stackToId");
        raw.put(7, new ArrayList<>(Arrays.asList(1, 2)));
        IntList converted = Mixins.call(OreDictionaryMixin.class, "coarctatio$ids", 7);
        assertEquals(new IntArrayList(new int[] {1, 2}), converted);
        // Stored back, so the conversion happens once
        assertSame(converted, raw.get(7));
        assertNull(Mixins.call(OreDictionaryMixin.class, "coarctatio$ids", 9));
    }

    @Test
    void registrationGoesStraightIntoThePrimitiveLists() {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        Mixins.call(OreDictionaryMixin.class, "registerOreImpl", "ingotIron", iron);

        int id = OreDictionaryMixin.getOreID("ingotIron");
        List<NonNullList<ItemStack>> stacks = Statics.get(OreDictionaryMixin.class, "idToStack");
        assertEquals(1, stacks.get(id).size());
        // The stack is copied, so a later edit by the caller cannot reach the registry
        assertNotSame(iron, stacks.get(id).get(0));
        assertArrayEquals(new int[] {id}, OreDictionaryMixin.getOreIDs(iron));

        // Registering the same pair again changes nothing
        Mixins.call(OreDictionaryMixin.class, "registerOreImpl", "ingotIron", iron);
        assertEquals(1, stacks.get(id).size());

        // The two refusals: the placeholder name and an empty stack
        Mixins.call(OreDictionaryMixin.class, "registerOreImpl", "Unknown", iron);
        Mixins.call(OreDictionaryMixin.class, "registerOreImpl", "ingotTin", ItemStack.EMPTY);
        assertFalse(OreDictionaryMixin.doesOreNameExist("Unknown"));
    }

    @Test
    void rebakingRebuildsTheLookupFromTheRegisteredStacks() {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        Mixins.call(OreDictionaryMixin.class, "registerOreImpl", "ingotIron", iron);
        int id = OreDictionaryMixin.getOreID("ingotIron");

        Int2ObjectOpenHashMap<IntList> stacks = Statics.get(OreDictionaryMixin.class, "stackToId");
        stacks.clear();
        // A stale null entry in the id list is skipped rather than crashing the rebake
        Statics.<List<NonNullList<ItemStack>>>get(OreDictionaryMixin.class, "idToStack").add(null);

        OreDictionaryMixin.rebakeMap();
        assertArrayEquals(new int[] {id}, OreDictionaryMixin.getOreIDs(iron));
    }

    @Test
    void theShadowedLookupIsAMixinPlaceholder() {
        assertNotNull(Mixins.instance(OreDictionaryMixin.class));
        // The body Mixin replaces with the real lookup; calls from the mixin are answered by the shadow stubs instead
        assertThrows(AssertionError.class, () -> Mixins.call(OreDictionaryMixin.class, "getOres", 0));
    }
}
