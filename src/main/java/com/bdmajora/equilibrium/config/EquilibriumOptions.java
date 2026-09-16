package com.bdmajora.equilibrium.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The option tree: every mixin.* rule, default and description declared once; an option's name is its mixin package path so it governs everything beneath it, and it is smaller than Lithium's since options that cannot remove work on 1.12.2 were dropped
public final class EquilibriumOptions {
    // Immutable, in declaration order; the config file and the GUI both present them this way.
    private static final Map<String, Entry> ENTRIES = build();

    private EquilibriumOptions() {
    }

    // One rule.
    public static final class Entry {
        private final String name;
        private final boolean enabledByDefault;
        private final String description;
        private final String nonVanilla;
        private final Map<String, Boolean> dependencies;

        Entry(String name, boolean enabledByDefault, String description, String nonVanilla,
              Map<String, Boolean> dependencies) {
            this.name = name;
            this.enabledByDefault = enabledByDefault;
            this.description = description;
            this.nonVanilla = nonVanilla;
            this.dependencies = dependencies;
        }

        // Full dotted rule name
        public String name() {
            return this.name;
        }

        // Value used when the user has not set the key
        public boolean enabledByDefault() {
            return this.enabledByDefault;
        }

        // Prose written into the config file above the key
        public String description() {
            return this.description;
        }

        // How behaviour differs from vanilla, or null when it does not.
        public String nonVanillaBehaviour() {
            return this.nonVanilla;
        }

        // Other rules this one requires, and the value each must hold
        public Map<String, Boolean> dependencies() {
            return this.dependencies;
        }

        // The name with the mixin. prefix removed, which is what the lang keys are built from.
        public String path() {
            return this.name.substring("mixin.".length());
        }

        // The top-level category, used to group the GUI tab into sections.
        public String category() {
            String path = this.path();
            int split = path.indexOf('.');
            return split == -1 ? path : path.substring(0, split);
        }

        // How deep this rule sits, so the GUI can indent children under their parent.
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

    // The whole tree in declaration order, which is the order the config file is written in
    public static Map<String, Entry> entries() {
        return ENTRIES;
    }

    // Single entry by rule name, or null when nothing declares it
    public static Entry get(String name) {
        return ENTRIES.get(name);
    }

    // The categories in declaration order, which is the order the GUI tab lists its groups in.
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

    // Entries under one heading, used to build a GUI group
    public static List<Entry> inCategory(String category) {
        List<Entry> entries = new ArrayList<>();

        for (Entry entry : ENTRIES.values()) {
            if (entry.category().equals(category)) {
                entries.add(entry);
            }
        }

        return entries;
    }

    // Declares the option tree once; order here is the order users see everywhere
    private static Map<String, Entry> build() {
        Builder builder = new Builder();

        // ------------------------------------------------------ advancements
        builder.add("mixin.advancements", true,
                "Advancement trigger optimizations");
        builder.add("mixin.advancements.inventory_trigger", true,
                "Every inventory change fires inventory_changed for every advancement listening to "
                        + "it, and each one walks the whole inventory matching every predicate against "
                        + "every stack. Changes that cannot satisfy any advancement are dropped before "
                        + "any scan, the slot tallies are computed once per change, and the changed "
                        + "stack must match a predicate before the inventory is walked (Universal "
                        + "Tweaks, Icterine).");

        // ---------------------------------------------------------------- ai
        builder.add("mixin.ai", true,
                "Mob AI optimizations");
        builder.add("mixin.ai.goal_selector", true,
                "The AI goal selector holds its task sets in fastutil linked open hash sets rather than "
                        + "java.util.LinkedHashSet. Both sets are walked every tick for every mob in "
                        + "range, and the vanilla ones put every task behind its own node.");

        // ------------------------------------------------------------- alloc
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

        // ------------------------------------------------------------- block
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

        // ------------------------------------------------------------- chunk
        builder.add("mixin.chunk", true,
                "Various world chunk optimizations");
        builder.add("mixin.chunk.no_validation", true,
                "Block reads no longer ask the world what type it is on every access in order to find "
                        + "out whether it is the debug world. The answer is fixed when the world is "
                        + "constructed, so it is resolved once.");
        builder.add("mixin.chunk.tile_entity_map", true,
                "A chunk's tile entity map is keyed by the packed long of the position rather than "
                        + "the BlockPos object, dropping the hash and equals dispatch and the node per "
                        + "entry on a map probed by every tile entity lookup. Iteration order is kept.");

        // ------------------------------------------------------- collections
        builder.add("mixin.collections", true,
                "Various collection optimizations");
        builder.add("mixin.collections.mob_spawning", true,
                "The set of chunks eligible for mob spawning is a fastutil open hash set rather than a "
                        + "java.util.HashSet. It is rebuilt every spawn cycle and probed once per "
                        + "candidate chunk per player.");

        // ------------------------------------------------------------ entity
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
        builder.add("mixin.entity.fast_elytra_check", true,
                "Skip writing to the tracked-data table every tick to record that a living entity is "
                        + "still not elytra flying. The write was already a no-op; the lookup it needed "
                        + "was not.");
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
        builder.add("mixin.entity.xp_orb_merging", false,
                "Once a second an experience orb absorbs every orb within a block and the survivor "
                        + "hands its whole value over in one pickup. A mob farm otherwise leaves dozens "
                        + "of orbs per kill, each a ticking, tracked entity that costs the player a "
                        + "two-tick pickup cooldown.",
                "One merged orb repairs Mending gear all at once and pickup sounds play once per merged orb.");
        builder.add("mixin.entity.fast_hand_swing", true,
                "Skip the hand swing progress and animation maths when the hand is not swinging. The "
                        + "division vanilla performs there needs the swing duration, and working that out "
                        + "means checking the entity's haste and mining fatigue effects.");

        // -------------------------------------------------------------- math
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

        // -------------------------------------------------------------- util
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

        // ------------------------------------------------------------- world
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

        // ----------------------------------------------------------- worldgen
        builder.add("mixin.worldgen", true,
                "World generation optimizations, after Noisium's approach of bypassing the abstractions "
                        + "between the generator and the storage it fills; every rule keeps generation "
                        + "bit-for-bit identical");
        builder.add("mixin.worldgen.primer_state_cache", true,
                "The chunk primer remembers the last state it encoded and the last id it decoded, so the "
                        + "long runs of stone and water terrain writes, and the column scans surface "
                        + "replacement reads back, skip the identity-map lookup per block.");
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

    // Small fluent helper so the tree above reads as a list rather than a wall of constructor calls.
    private static final class Builder {
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Map<String, Map<String, Boolean>> dependencies = new LinkedHashMap<>();
        private String current;

        Builder add(String name, boolean enabledByDefault, String description) {
            return this.add(name, enabledByDefault, description, null);
        }

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

        Builder requires(String dependency, boolean requiredValue) {
            this.dependencies.get(this.current).put(dependency, requiredValue);
            return this;
        }

        Map<String, Entry> finish() {
            // Every dependency must name a real option; a typo would silently disable nothing and only show up as "why is this mixin still applied", so fail at class-init
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
