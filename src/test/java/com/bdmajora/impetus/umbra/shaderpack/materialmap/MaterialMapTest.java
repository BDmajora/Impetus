package com.bdmajora.impetus.umbra.shaderpack.materialmap;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;
import net.minecraft.util.BlockRenderLayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialMapTest {
    private static String describe(BlockEntry entry) {
        return entry.getId() + (entry.getPropertyPredicates().isEmpty() ? "" : new java.util.TreeMap<>(entry.getPropertyPredicates()).toString());
    }

    private static List<String> describe(List<BlockEntry> entries) {
        return entries.stream().map(MaterialMapTest::describe).toList();
    }

    @Test
    void entriesParseEveryShapeIrisAccepts() {
        assertThrows(IllegalArgumentException.class, () -> BlockEntry.parse(""));
        assertEquals("minecraft:stone", describe((BlockEntry) BlockEntry.parse("stone")));
        assertEquals("mod:block", describe((BlockEntry) BlockEntry.parse("mod::block")));
        assertEquals("minecraft:tall_grass{half=upper}", describe((BlockEntry) BlockEntry.parse("tall_grass:half=upper")));
        // A malformed predicate is skipped and the rest kept
        assertEquals("minecraft:tall_grass{half=upper}", describe((BlockEntry) BlockEntry.parse("minecraft:tall_grass:half=upper:broken")));
        TagEntry tag = (TagEntry) BlockEntry.parse("%leaves");
        assertEquals(new NamespacedId("minecraft", "leaves"), tag.getId());
        assertTrue(tag.getPropertyPredicates().isEmpty());
        assertEquals("forge:ores", ((TagEntry) BlockEntry.parse("%forge:ores")).getId().toString());
        assertEquals(Map.of("axis", "y"), ((TagEntry) BlockEntry.parse("%logs:axis=y")).getPropertyPredicates());
    }

    @Test
    void idsAreNamespacedDefaultingToMinecraft() {
        NamespacedId stone = new NamespacedId("stone");
        assertEquals("minecraft", stone.getNamespace());
        assertEquals("stone", stone.getName());
        assertEquals("mod:a:b", new NamespacedId("mod:a:b").toString());
        assertEquals("a:b", new NamespacedId("mod:a:b").getName());
        assertEquals(stone, new NamespacedId("minecraft", "stone"));
        assertEquals(stone, stone);
        assertEquals(stone.hashCode(), new NamespacedId("minecraft:stone").hashCode());
        assertNotEquals(stone, null);
        assertNotEquals(stone, "minecraft:stone");
        assertNotEquals(stone, new NamespacedId("mod", "stone"));
        assertNotEquals(stone, new NamespacedId("minecraft", "dirt"));
    }

    private static List<String> translate(String token) {
        return describe(ModernBlockNames.translate((BlockEntry) BlockEntry.parse(token)));
    }

    @Test
    void modernNamesTranslateToTheirLegacyBlocks() {
        assertEquals(List.of("mod:water"), translate("mod:water"));
        assertEquals(List.of("minecraft:water", "minecraft:flowing_water"), translate("water"));
        // The pack's predicates carry over, the translation's own win a collision
        assertEquals(List.of("minecraft:tallgrass{snowy=false, type=tall_grass}"), translate("grass:snowy=false:type=fern"));
        assertEquals(List.of("minecraft:lit_redstone_lamp{lit=true}"), translate("redstone_lamp:lit=true"));
        assertEquals(List.of("minecraft:lit_furnace{lit=true}"), translate("furnace:lit=true"));
        assertEquals(List.of("minecraft:furnace{lit=false}"), translate("furnace:lit=false"));
        assertEquals(List.of("minecraft:wool{color=silver}"), translate("light_gray_wool"));
        assertEquals(List.of("minecraft:concrete{color=red}"), translate("red_concrete"));
        assertEquals(List.of("minecraft:silver_shulker_box"), translate("light_gray_shulker_box"));
        assertEquals(List.of("minecraft:white_glazed_terracotta"), translate("white_glazed_terracotta"));
        assertEquals(List.of("minecraft:_wool"), translate("_wool"));
        assertEquals(List.of("minecraft:plaid_wool"), translate("plaid_wool"));
        assertEquals(List.of("minecraft:stone"), translate("stone"));
    }

    private static IdMap idMap(String blocks, String items, String entities) {
        Map<AbsolutePackPath, String> sources = new java.util.HashMap<>();
        if (blocks != null) {
            sources.put(AbsolutePackPath.fromAbsolutePath("/block.properties"), blocks);
        }
        if (items != null) {
            sources.put(AbsolutePackPath.fromAbsolutePath("/item.properties"), items);
        }
        if (entities != null) {
            sources.put(AbsolutePackPath.fromAbsolutePath("/entity.properties"), entities);
        }
        return new IdMap(sources, Map.of("MC_VERSION", "11202"));
    }

    @Test
    void blockMapsKeepDeclarationOrderAndRecoverTypos() {
        IdMap map = idMap(String.join("\n",
                "block.10 = minecraft:gold_oreminecraft:redstone_oreminecraft:coal_ore stone",
                "block.11 = mymod:amymod:b mymod:c:facing=up minecraft:stone:variant=granite a:b:c %minecraft:leaves",
                "block.12 = x:minecraftlonger:y",
                "block.x = stone",
                "block.13",
                "layer.cutout = minecraft:glass:color=red %minecraft:leaves  mod:pane",
                "layer.solid = stone",
                "layer.cutout_mipped = leaves",
                "layer.glowing = torch",
                "layer.translucent",
                "# layer.cutout = ignored"), null, null);
        assertTrue(map.hasBlockProperties());
        Map<Integer, List<BlockEntry>> blocks = map.getBlockProperties();
        assertEquals(List.of(10, 11, 12), List.copyOf(blocks.keySet()));
        assertEquals(List.of("minecraft:gold_ore", "minecraft:redstone_ore", "minecraft:coal_ore", "minecraft:stone"), describe(blocks.get(10)));
        // The enclosing namespace splits a typo, predicates never do, a trailing segment that is no namespace is a bad predicate, and tags are dropped
        assertEquals(List.of("mymod:a", "mymod:b", "mymod:c{facing=up}", "minecraft:stone{variant=granite}", "a:b"), describe(blocks.get(11)));
        assertEquals(List.of("x:minecraftlonger"), describe(blocks.get(12)));
        assertEquals(Map.of(new NamespacedId("minecraft:glass"), BlockRenderLayer.CUTOUT, new NamespacedId("mod:pane"), BlockRenderLayer.CUTOUT,
                        new NamespacedId("stone"), BlockRenderLayer.SOLID, new NamespacedId("leaves"), BlockRenderLayer.CUTOUT_MIPPED),
                map.getBlockRenderLayerMap());
    }

    @Test
    void aModernOnlyMapIsReadThroughTheFlatteningTable() {
        String modern = String.join("\n",
                "#if MC_VERSION >= 11300",
                "block.1 = water grass_block:snowy=false",
                "#endif");
        IdMap map = idMap(modern, null, null);
        assertEquals(List.of("minecraft:water", "minecraft:flowing_water", "minecraft:grass{snowy=false}"), describe(map.getBlockProperties().get(1)));
        // A map that is empty either way stays empty
        assertTrue(idMap("#if MC_VERSION >= 99999\nblock.1 = stone\n#endif", null, null).getBlockProperties().isEmpty());
        IdMap none = idMap(null, null, null);
        assertFalse(none.hasBlockProperties());
        assertTrue(none.getBlockProperties().isEmpty());
        assertTrue(none.getBlockRenderLayerMap().isEmpty());
        assertTrue(none.getItemIdMap().isEmpty());
        assertTrue(none.getEntityIdMap().isEmpty());
    }

    @Test
    void itemAndEntityMapsTakePlainNames() {
        IdMap map = idMap(null, "item.5 = diamond_sword  mod:wand wand:damage=3\nitem.bad = stick\nitem.6", "entity.7 = zombie");
        assertEquals(Map.of(new NamespacedId("diamond_sword"), 5, new NamespacedId("mod:wand"), 5), map.getItemIdMap());
        assertEquals(Map.of(new NamespacedId("zombie"), 7), map.getEntityIdMap());
    }
}
