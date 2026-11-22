package com.bdmajora.equilibrium.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Catalogue of every switch with its description.
public final class EquilibriumOptions {
    // Every switch by name.
    private static final Map<String, Entry> ENTRIES = build();

    // Prevents instantiation
    private EquilibriumOptions() {
    }

    // Description of one switch.
    public static final class Entry {
        // Name of the switch.
        private final String name;
        // Default value.
        private final boolean enabledByDefault;
        // Text that explains the switch.
        private final String description;
        // Text that explains differences from the base game or null.
        private final String nonVanilla;
        // Required values of other switches.
        private final Map<String, Boolean> dependencies;

        // Creates an entry.
        Entry(String name, boolean enabledByDefault, String description, String nonVanilla,
              Map<String, Boolean> dependencies) {
            this.name = name;
            this.enabledByDefault = enabledByDefault;
            this.description = description;
            this.nonVanilla = nonVanilla;
            this.dependencies = dependencies;
        }

        // Returns the name.
        public String name() {
            return this.name;
        }

        // Returns the default value.
        public boolean enabledByDefault() {
            return this.enabledByDefault;
        }

        // Returns the description.
        public String description() {
            return this.description;
        }

        // Returns the text about differences from the base game or null.
        public String nonVanillaBehaviour() {
            return this.nonVanilla;
        }

        // Returns the required values of other switches.
        public Map<String, Boolean> dependencies() {
            return this.dependencies;
        }

        // Returns the name without its prefix.
        public String path() {
            return this.name.substring("mixin.".length());
        }

        // Returns the first part of the path.
        public String category() {
            String path = this.path();
            int split = path.indexOf('.');
            return split == -1 ? path : path.substring(0, split);
        }

        // Returns how deep the switch sits below the prefix.
        public int depth() {
            int depth = 0;

            for (int i = 0; i < this.name.length(); i++) {
                if (this.name.charAt(i) == '.') {
                    depth++;
                }
            }

            return depth - 1;
        }
    }

    // Returns every entry by name.
    public static Map<String, Entry> entries() {
        return ENTRIES;
    }

    // Returns the entry of the name or null.
    public static Entry get(String name) {
        return ENTRIES.get(name);
    }

    // Returns the categories in order of first use.
    public static List<String> categories() {
        List<String> categories = new ArrayList<>();

        for (Entry entry : ENTRIES.values()) {
            String category = entry.category();

            if (!categories.contains(category)) {
                categories.add(category);
            }
        }

        return categories;
    }

    // Returns the entries of the category.
    public static List<Entry> inCategory(String category) {
        List<Entry> entries = new ArrayList<>();

        for (Entry entry : ENTRIES.values()) {
            if (entry.category().equals(category)) {
                entries.add(entry);
            }
        }

        return entries;
    }

    // Builds the catalogue.
    private static Map<String, Entry> build() {
        Builder builder = new Builder();

        builder.add("mixin.advancements", true,
                "Advancement trigger optimizations");
        builder.add("mixin.advancements.inventory_trigger", true,
                "Every inventory change fires inventory_changed for every advancement listening to "
                        + "it, and each one walks the whole inventory matching every predicate against "
                        + "every stack. Changes that cannot satisfy any advancement are dropped before "
                        + "any scan, the slot tallies are computed once per change, and the changed "
                        + "stack must match a predicate before the inventory is walked (Universal "
                        + "Tweaks, Icterine).");

        builder.add("mixin.ai", true,
                "Mob AI optimizations");
        builder.add("mixin.ai.goal_selector", true,
                "The AI goal selector holds its task sets in fastutil linked open hash sets rather than "
                        + "java.util.LinkedHashSet. Both sets are walked every tick for every mob in "
                        + "range, and the vanilla ones put every task behind its own node.");
        builder.add("mixin.ai.remove_look_tasks", false,
                "Drops the two cosmetic look tasks when a mob registers its AI (Universal Tweaks, AI "
                        + "Improvements). Watching the nearest player runs an entity search around every idle "
                        + "mob each tick, and idle looking only picks a random heading.",
                "Mobs no longer turn their heads toward players or glance around while idle.");

        builder.add("mixin.alloc", true,
                "Patches that reduce memory allocations");
        builder.add("mixin.alloc.entity_tracker", true,
                "Entity trackers store their players in a fastutil open hash set instead of a "
                        + "java.util.HashSet, which allocates a node per player per tracked entity.");
        builder.add("mixin.alloc.deep_passengers", true,
                "Collecting an entity's passengers stops allocating a hash map for the overwhelmingly "
                        + "common case of an entity carrying nobody, and uses an identity set when it "
                        + "does carry someone.");
        builder.add("mixin.alloc.enum_values", true,
                "Avoid Enum#values() array copies in frequently called code");
        builder.add("mixin.alloc.enum_values.piston_block", true,
                "Piston extension checks reuse the shared facing array instead of copying "
                        + "EnumFacing.values().");
        builder.add("mixin.alloc.enum_values.piston_handler", true,
                "The piston structure resolver reuses the shared facing array instead of copying "
                        + "EnumFacing.values() once per block it moves.");
        builder.add("mixin.alloc.enum_values.redstone_wire", true,
                "Redstone wire power propagation reuses the shared facing array instead of copying "
                        + "EnumFacing.values() once per wire per power level.");

        builder.add("mixin.block", true,
                "Optimizations related to blocks");
        builder.add("mixin.block.hopper", true,
                "Hoppers remember the inventory above them and the one they face, instead of searching "
                        + "the world for both on every transfer attempt. An idle hopper attempts a "
                        + "transfer every tick, and each attempt that finds nothing costs a full entity "
                        + "query over the block.")
                .requires("mixin.util.block_entity_retrieval", true)
                .requires("mixin.util.data_storage", true);
        builder.add("mixin.block.redstone_wire", true,
                "Redstone wire power calculation reads each neighbouring block once instead of twice, "
                        + "and resolves the block above the wire once per update rather than once per "
                        + "direction.");
        builder.add("mixin.block.crafting_cache", true,
                "Remembers which recipe matched a crafting grid by the items and metadata in it, so the "
                        + "next identical grid skips the walk over every registered recipe (Universal Tweaks). "
                        + "Grids holding NBT are never cached and a hit is re-verified with the recipe's own "
                        + "matches().");
        builder.add("mixin.block.furnace_recipes", true,
                "Smelting lookups consult a per-item index over the furnace recipe table instead of "
                        + "comparing the input against every entry (Chibi). The table itself is untouched, so "
                        + "registration order and metadata wildcards behave exactly as before.");
        builder.add("mixin.block.spawner_check_cache", true,
                "A spawner's player-in-range check is held for ten ticks instead of repeated every tick, "
                        + "and redone at once if the player count changes (FoamFix).");

        builder.add("mixin.chunk", true,
                "Various world chunk optimizations");
        builder.add("mixin.chunk.tile_entity_map", true,
                "A chunk's tile entity map is keyed by the packed long of the position rather than "
                        + "the BlockPos object, dropping the hash and equals dispatch and the node per "
                        + "entry on a map probed by every tile entity lookup. Iteration order is kept.");

        builder.add("mixin.collections", true,
                "Various collection optimizations");
        builder.add("mixin.collections.mob_spawning", true,
                "The set of chunks eligible for mob spawning is a fastutil open hash set rather than a "
                        + "java.util.HashSet. It is rebuilt every spawn cycle and probed once per "
                        + "candidate chunk per player.");

        builder.add("mixin.entity", true,
                "Various entity optimizations");
        builder.add("mixin.entity.collisions", true,
                "Various entity collision optimizations");
        builder.add("mixin.entity.collisions.reduced_radius", true,
                "World.MAX_ENTITY_RADIUS is a global any mod with a large entity raises for everyone, "
                        + "and every bounding-box query pads by it, so one such mod makes each mob's "
                        + "per-tick pushing check scan up to nine chunks. Vanilla's own entities, whose "
                        + "size is known, run the check with vanilla's two-block padding again. Does "
                        + "nothing until a mod raises the radius.");
        builder.add("mixin.entity.collisions.movement", true,
                "Gathering the blocks an entity could collide with resolves a chunk section once per "
                        + "column rather than once per block. The search walks a column at a time, so "
                        + "sixteen consecutive reads share one section.")
                .requires("mixin.util.chunk_access", true);
        builder.add("mixin.entity.fast_retrieval", true,
                "Entity queries resolve each chunk once instead of testing whether it is loaded and then "
                        + "fetching the chunk that test just found.")
                .requires("mixin.util.chunk_access", true);
        builder.add("mixin.entity.flag_cache", true,
                "The synced flags byte behind isSneaking, isSprinting, isInvisible, isGlowing, isBurning "
                        + "and isElytraFlying is read once and kept until the data manager writes it, "
                        + "instead of going through the data manager's lock and a Byte unboxing on every "
                        + "call. Rendering asks several of these per entity per frame.");
        builder.add("mixin.entity.fast_spawn_preparation", true,
                "Forge constructs every spawned entity through Constructor.newInstance. A method "
                        + "handle lambda bound to the (World) constructor is spun once per entity type "
                        + "instead, which the JIT can inline; types the lookup cannot reach keep Forge's "
                        + "factory.");
        builder.add("mixin.entity.tracker_vertical_range", false,
                "Whether a player receives an entity's updates is decided on horizontal distance alone, "
                        + "so a player at bedrock is sent every mob on the surface above. The tracking "
                        + "range is applied vertically as well.",
                "Entities more than their tracking range above or below a player are not sent to them, "
                        + "which shows on tall builds.");
        builder.add("mixin.entity.fast_hand_swing", true,
                "Skip the hand swing progress and animation maths when the hand is not swinging. The "
                        + "division vanilla performs there needs the swing duration, and working that out "
                        + "means checking the entity's haste and mining fatigue effects.");

        builder.add("mixin.math", true,
                "Various math optimizations");
        builder.add("mixin.math.fast_blockpos", true,
                "Directional block position offsets are computed inline, and each direction's offsets "
                        + "are stored rather than derived from its axis on every read.");
        builder.add("mixin.math.fast_util", true,
                "Direction opposites avoid a modulo, and picking a random direction stops cloning the "
                        + "values array twice per call.");
        builder.add("mixin.math.sine_lut", true,
                "Replaces the 256 KB sine table with a 64 KB one that fits in cache, using "
                        + "trigonometric identities to reconstruct the quadrants it drops. Results are "
                        + "bit-for-bit identical to vanilla, and verified to be at startup.");

        builder.add("mixin.util", true,
                "Various utilities for other mixins. These add no optimization on their own; turning "
                        + "one off disables everything that depends on it.");
        builder.add("mixin.util.block_entity_retrieval", true,
                "Allows looking up a tile entity that already exists without constructing and "
                        + "registering one as a side effect of asking.");
        builder.add("mixin.util.data_storage", true,
                "Stores Equilibrium's per-world scratch data. None of it is saved; it exists so the "
                        + "other optimizations have somewhere to keep their counters.");
        builder.add("mixin.util.chunk_access", true,
                "Access a world's loaded chunks directly, without going through the chunk provider's "
                        + "dispatch and its map lookup.");

        builder.add("mixin.world", true,
                "Various world related optimizations");
        builder.add("mixin.world.chunk_gen_limit", false,
                "Chunk sending generates up to 49 chunks per tick as long as it stays under 50 ms, "
                        + "which is the whole tick. The cap becomes 24 chunks and 25 ms so terrain "
                        + "loading spreads over more ticks and entities keep their share of each one.",
                "Terrain loads more slowly on a server that had headroom to spare.");
        builder.add("mixin.world.entity_cleanup", true,
                "Once a dimension has had no players for 300 ticks it stops ticking entities, and with "
                        + "them the step that removes entities and tile entities unloaded with their "
                        + "chunks, so those lists grow until a player returns. The removal step runs on "
                        + "those ticks anyway.");
        builder.add("mixin.world.entity_tick_budget", false,
                "Once the previous server tick ran over 40 ms, ordinary living entities, items and "
                        + "orbs more than 96 blocks from every player tick every other tick until the "
                        + "server catches up. Nothing near a player, ridden, or a boss is touched, and "
                        + "at normal tick times nothing changes.",
                "Far-off mobs, drops and farms run at half speed while the server is behind.");
        builder.add("mixin.world.ghost_chunks", true,
                "Beds, farmland and Forge fluids read neighbouring blocks on update, and a read into an "
                        + "unloaded chunk loads it, so a bed or field on a chunk border keeps its "
                        + "neighbour loaded and generating forever. Those reads now treat an unloaded "
                        + "chunk as absent: a bed skips the update, farmland sees no water there, and a "
                        + "fluid waits until its surroundings are loaded, as vanilla liquids already do.");
        builder.add("mixin.world.spawn_chunks", true,
                "Skips generating the 625-chunk spawn area before the world opens. Only the chunk "
                        + "holding the spawn point is prepared; the rest load on demand, and once loaded "
                        + "are kept resident exactly as vanilla keeps its spawn chunks.",
                "Spawn-area redstone and farms only run once a player has been near them since launch.");
        builder.add("mixin.world.block_entity_ticking", true,
                "Various tile entity ticking optimizations");
        builder.add("mixin.world.block_entity_ticking.sleeping", true,
                "Allows tile entities to skip work they can prove will do nothing.");
        builder.add("mixin.world.block_entity_ticking.sleeping.brewing_stand", true,
                "A brewing stand with no ingredient stops asking the brewing registry whether it can "
                        + "brew. On Forge that question walks every recipe every mod has registered, and "
                        + "it is asked once per stand per tick.");
        builder.add("mixin.world.block_entity_ticking.sleeping.furnace", true,
                "A furnace that is unlit, has nothing part-cooked and is missing either its fuel or its "
                        + "input skips its tick entirely. Every branch it would have taken is a no-op.");
        builder.add("mixin.world.explosions", true,
                "Various improvements to explosions.");
        builder.add("mixin.world.explosions.block_raycast", true,
                "Explosion block damage walks its 1352 rays through a chunk-section cursor and reads "
                        + "each position at most once, instead of a fresh world lookup and a fresh block "
                        + "position for every step of every ray.");
        builder.add("mixin.world.explosions.entity_raycast", true,
                "Explosion entity exposure caches the blocks its sample rays cross. Every ray of the "
                        + "same explosion crosses mostly the same blocks, and vanilla re-traces all of "
                        + "them for every entity.");
        builder.add("mixin.world.inline_block_access", true,
                "Block and block-state reads resolve the chunk through a small direct cache rather than "
                        + "the chunk provider's map lookup on every access.")
                .requires("mixin.util.chunk_access", true);
        builder.add("mixin.world.inline_height", true,
                "World height queries resolve the chunk once instead of testing whether it is loaded and "
                        + "then fetching the chunk that test just found.")
                .requires("mixin.util.chunk_access", true);
        builder.add("mixin.world.raycast", true,
                "Block ray tracing steps through the world without allocating a vector and a block "
                        + "position per step.");
        builder.add("mixin.world.tick_scheduler", true,
                "The set of pending scheduled block ticks is a fastutil open hash set rather than a "
                        + "java.util.HashSet, which removes a node allocation per scheduled tick and a "
                        + "pointer chase from every lookup. Redstone, liquids and crops schedule "
                        + "thousands of these per second on a busy world.");

        builder.add("mixin.worldgen", true,
                "World generation optimizations, after Noisium's approach of bypassing the abstractions "
                        + "between the generator and the storage it fills; every rule keeps generation "
                        + "bit-for-bit identical");
        builder.add("mixin.worldgen.chunk_copy", true,
                "Copying a finished primer into a chunk no longer reads back the slot it is about to "
                        + "write: every slot of a new section is air, so the counters are updated "
                        + "directly and the state goes straight into the palette storage.");
        builder.add("mixin.worldgen.int_cache", true,
                "The biome layer scratch arrays are pooled per thread instead of in one locked global "
                        + "pool. Vanilla's pool hands every in-use array back on reset, so two threads "
                        + "walking the layers at once would reclaim each other's buffers.");

        return builder.finish();
    }

    // Builder of the catalogue.
    private static final class Builder {
        // Entries added so far.
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        // Dependencies of each entry.
        private final Map<String, Map<String, Boolean>> dependencies = new LinkedHashMap<>();
        // Name of the entry added last.
        private String current;

        // Adds an entry without a note about differences.
        Builder add(String name, boolean enabledByDefault, String description) {
            return this.add(name, enabledByDefault, description, null);
        }

        // Adds an entry and rejects a duplicate.
        Builder add(String name, boolean enabledByDefault, String description, String nonVanilla) {
            Map<String, Boolean> deps = new LinkedHashMap<>();

            if (this.entries.put(name, new Entry(name, enabledByDefault, description, nonVanilla,
                    Collections.unmodifiableMap(deps))) != null) {
                throw new IllegalStateException("Duplicate option: " + name);
            }

            this.dependencies.put(name, deps);
            this.current = name;
            return this;
        }

        // Adds a dependency to the entry added last.
        Builder requires(String dependency, boolean requiredValue) {
            if (this.current == null) {
                throw new IllegalStateException("requires() needs a preceding add()");
            }

            this.dependencies.get(this.current).put(dependency, requiredValue);
            return this;
        }

        // Returns the entries and rejects a dependency on an unknown switch.
        Map<String, Entry> finish() {
            for (Map.Entry<String, Map<String, Boolean>> entry : this.dependencies.entrySet()) {
                for (String dependency : entry.getValue().keySet()) {
                    if (!this.entries.containsKey(dependency)) {
                        throw new IllegalStateException(
                                "Option '" + entry.getKey() + "' depends on unknown option '" + dependency + "'");
                    }
                }
            }

            return Collections.unmodifiableMap(this.entries);
        }
    }
}
