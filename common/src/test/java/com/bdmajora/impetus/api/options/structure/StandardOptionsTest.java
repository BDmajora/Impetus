package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.testing.FreshClasses;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class StandardOptionsTest {
    private static final List<String> GROUPS = List.of(
            "RENDERING minecraft:rendering",
            "WINDOW minecraft:window",
            "INDICATORS minecraft:indicators",
            "GRAPHICS minecraft:graphics",
            "MIPMAPS minecraft:mipmaps",
            "DETAILS minecraft:details",
            "CHUNK_UPDATES impetus:chunk_updates",
            "RENDERING_CULLING impetus:rendering_culling",
            "CPU_SAVING impetus:cpu_saving",
            "SORTING impetus:sorting",
            "LIGHTING impetus:lighting",
            "FILTERING impetus:filtering",
            "FLUIDS impetus:fluids",
            "MESH_TERRAIN impetus:mesh_terrain",
            "FLUIDLOGGING impetus:fluidlogging");

    private static final List<String> PAGES = List.of(
            "GENERAL impetus:general",
            "QUALITY impetus:quality",
            "PERFORMANCE impetus:performance",
            "NVIDIUM impetus:nvidium",
            "FLUIDLOGGING impetus:fluidlogging",
            "ADVANCED impetus:advanced",
            "SHADERS impetus:shaders");

    private static final List<String> OPTIONS = List.of(
            "RENDER_DISTANCE minecraft:render_distance",
            "SIMULATION_DISTANCE minecraft:simulation_distance",
            "BRIGHTNESS minecraft:brightness",
            "GUI_SCALE minecraft:gui_scale",
            "FULLSCREEN minecraft:fullscreen",
            "FULLSCREEN_RESOLUTION minecraft:fullscreen_resolution",
            "VSYNC minecraft:vsync",
            "MAX_FRAMERATE minecraft:max_frame_rate",
            "VIEW_BOBBING minecraft:view_bobbing",
            "INACTIVITY_FPS_LIMIT minecraft:inactivity_fps_limit",
            "ATTACK_INDICATOR minecraft:attack_indicator",
            "AUTOSAVE_INDICATOR minecraft:autosave_indicator",
            "GRAPHICS_API minecraft:graphics_api",
            "GRAPHICS_MODE minecraft:graphics_mode",
            "CLOUDS minecraft:clouds",
            "CLOUD_HEIGHT minecraft:cloud_height",
            "CLOUD_DISTANCE minecraft:cloud_distance",
            "WEATHER minecraft:weather",
            "WEATHER_EFFECT_RADIUS minecraft:weather_effect_radius",
            "LEAVES minecraft:leaves",
            "SEE_THROUGH_LEAVES minecraft:see_through_leaves",
            "PARTICLES minecraft:particles",
            "SMOOTH_LIGHT minecraft:smooth_lighting",
            "BIOME_BLEND minecraft:biome_blend",
            "ENTITY_DISTANCE minecraft:entity_distance",
            "ENTITY_SHADOWS minecraft:entity_shadows",
            "VIGNETTE minecraft:vignette",
            "MIPMAP_LEVEL minecraft:mipmap_levels",
            "TEXTURE_FILTERING minecraft:texture_filtering",
            "ANISOTROPIC_FILTERING minecraft:anisotropic_filtering",
            "TEXEL_INTERPOLATION minecraft:texel_interpolation",
            "CHUNK_UPDATE_THREADS impetus:chunk_update_threads",
            "DEFFER_CHUNK_UPDATES impetus:defer_chunk_updates",
            "BLOCK_FACE_CULLING impetus:block_face_culling",
            "COMPACT_VERTEX_FORMAT impetus:compact_vertex_format",
            "FOG_OCCLUSION impetus:fog_occlusion",
            "ENTITY_CULLING impetus:entity_culling",
            "ANIMATE_VISIBLE_TEXTURES impetus:animate_only_visible_textures",
            "NO_ERROR_CONTEXT impetus:no_error_context",
            "PERSISTENT_MAPPING impetus:persistent_mapping",
            "MULTI_DRAW_INDIRECT impetus:multi_draw_indirect",
            "CPU_FRAMES_AHEAD impetus:cpu_render_ahead_limit",
            "TRANSLUCENT_FACE_SORTING impetus:translucent_face_sorting",
            "USE_QUAD_NORMALS_FOR_LIGHTING impetus:use_quad_normals_for_lighting",
            "RENDER_PASS_OPTIMIZATION impetus:render_pass_optimization",
            "RENDER_PASS_CONSOLIDATION impetus:render_pass_consolidation",
            "USE_FASTER_CLOUDS impetus:use_faster_clouds",
            "ASYNC_GRAPH_SEARCH impetus:async_graph_search",
            "CHUNK_FADE_IN_DURATION impetus:chunk_fade_in_duration",
            "RASTER_OCCLUSION_CULLING impetus:raster_occlusion_culling",
            "IMPROVED_TRANSPARENCY impetus:improved_transparency",
            "FLUID_CULLING impetus:fluid_culling",
            "FLUID_SHAPING impetus:fluid_shaping",
            "INFERRED_FLUIDLOGGING impetus:inferred_fluidlogging",
            "ENTITY_SORTING impetus:entity_sorting",
            "FAST_BLOCK_RENDERER impetus:fast_block_renderer",
            "MEMORY_TRACING impetus:memory_tracing",
            "SHOW_TOASTS impetus:show_toasts",
            "INCOMPATIBLE_PACK_WARNINGS impetus:incompatible_pack_warnings",
            "MESH_TEMPORAL_COHERENCE impetus:mesh_temporal_coherence",
            "MESH_TRANSLUCENCY_SORTING impetus:mesh_translucency_sorting",
            "MESH_AUTOMATIC_MEMORY impetus:mesh_automatic_memory",
            "MESH_MAX_MEMORY impetus:mesh_max_geometry_memory",
            "MESH_KEEP_DISTANCE impetus:mesh_region_keep_distance",
            "MESH_STATISTICS impetus:mesh_statistics");

    private static final Map<Class<?>, List<String>> HOLDERS = Map.of(
            StandardOptions.Group.class, GROUPS,
            StandardOptions.Pages.class, PAGES,
            StandardOptions.Option.class, OPTIONS);

    private static final Pattern WORDS = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");

    // Field name to "mod:path" from the pinned lines
    private static Map<String, String> pinned(List<String> lines) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (String line : lines) {
            String[] parts = line.split(" ");
            assertNull(ids.put(parts[0], parts[1]), parts[0]);
        }
        return ids;
    }

    // Every static field of a holder by name, read through reflection so a holder from another loader works too
    private static Map<String, Object> declared(Class<?> holder) throws IllegalAccessException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : holder.getDeclaredFields()) {
            if (!field.isSynthetic() && Modifier.isStatic(field.getModifiers())) {
                values.put(field.getName(), field.get(null));
            }
        }
        return values;
    }

    private static Map<String, String> names(Map<String, Object> values) {
        Map<String, String> names = new LinkedHashMap<>();
        values.forEach((name, value) -> names.put(name, String.valueOf(value)));
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Set<OptionIdentifier<?>> ids(Class<?> holder) throws IllegalAccessException {
        Set<OptionIdentifier<?>> ids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object value : declared(holder).values()) {
            ids.add((OptionIdentifier<?>) value);
        }
        return ids;
    }

    private static Set<OptionIdentifier<?>> shared(Collection<OptionIdentifier<?>> a, Collection<OptionIdentifier<?>> b) {
        Set<OptionIdentifier<?>> both = Collections.newSetFromMap(new IdentityHashMap<>());
        both.addAll(a);
        both.retainAll(b);
        return both;
    }

    @Test
    void everyHolderNamesExactlyThePinnedIds() throws IllegalAccessException {
        for (Map.Entry<Class<?>, List<String>> holder : HOLDERS.entrySet()) {
            assertEquals(pinned(holder.getValue()), names(declared(holder.getKey())), holder.getKey().getSimpleName());
        }
        assertEquals(15, GROUPS.size());
        assertEquals(7, PAGES.size());
        assertEquals(65, OPTIONS.size());
        // The outer class shares no ids of its own
        assertTrue(Arrays.stream(StandardOptions.class.getDeclaredFields()).noneMatch(f -> Modifier.isPublic(f.getModifiers())));
    }

    @Test
    void everyIdIsAPublicStaticFinalUntypedInternedId() throws IllegalAccessException {
        for (Class<?> holder : HOLDERS.keySet()) {
            for (Field field : holder.getDeclaredFields()) {
                String name = holder.getSimpleName() + "." + field.getName();
                int modifiers = field.getModifiers();
                assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), name);
                assertSame(OptionIdentifier.class, field.getType(), name);
                assertEquals(OptionIdentifier.class.getName() + "<java.lang.Void>", field.getGenericType().getTypeName(), name);
                OptionIdentifier<?> id = (OptionIdentifier<?>) field.get(null);
                assertNotNull(id, name);
                assertSame(void.class, id.getType(), name);
                assertTrue(OptionIdentifier.isPresent(id), name);
                assertNotSame(OptionGroup.DEFAULT_ID, id, name);
                // Whichever way the pair is asked for, it is this one object
                assertSame(id, OptionIdentifier.create(id.getModId(), id.getPath()), name);
                assertSame(id, OptionIdentifier.create(id.getModId(), id.getPath(), void.class), name);
                assertSame(id, OptionIdentifier.intern(id.getModId(), id.getPath()), name);
                // A mod typing the pair after the holder made it is refused at its own call and changes nothing
                IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> OptionIdentifier.create(id.getModId(), id.getPath(), Integer.class), name);
                assertTrue(refused.getMessage().contains("'" + id + "'"), refused.getMessage());
                assertSame(id, OptionIdentifier.create(id.getModId(), id.getPath()), name);
                assertSame(void.class, id.getType(), name);
            }
        }
    }

    @Test
    void idsAreDistinctWithinEachHolderAndOnlyTheFluidloggingPageSharesAGroupId() throws IllegalAccessException {
        Set<OptionIdentifier<?>> groups = ids(StandardOptions.Group.class);
        Set<OptionIdentifier<?>> pages = ids(StandardOptions.Pages.class);
        Set<OptionIdentifier<?>> options = ids(StandardOptions.Option.class);
        assertEquals(GROUPS.size(), groups.size());
        assertEquals(PAGES.size(), pages.size());
        assertEquals(OPTIONS.size(), options.size());
        // The fluidlogging page and its lone group are one id, which ConfigApi routes apart by registry
        assertSame(StandardOptions.Group.FLUIDLOGGING, StandardOptions.Pages.FLUIDLOGGING);
        assertEquals(Set.of(StandardOptions.Group.FLUIDLOGGING), shared(groups, pages));
        // Option ids are untyped too, so one named like a group or page would be that very object
        assertEquals(Set.of(), shared(options, groups));
        assertEquals(Set.of(), shared(options, pages));
    }

    @Test
    void idsAreLowercaseWordsInTheTwoBuiltInNamespaces() throws IllegalAccessException {
        for (Class<?> holder : HOLDERS.keySet()) {
            for (OptionIdentifier<?> id : ids(holder)) {
                assertTrue(Set.of("minecraft", "impetus").contains(id.getModId()), id.toString());
                // No ':' or '/', so toString and the controller's search_results/<path> ids stay unambiguous
                assertTrue(WORDS.matcher(id.getPath()).matches(), id.toString());
                assertEquals(id.getModId() + ":" + id.getPath(), id.toString());
            }
        }
        // Every built-in page is Impetus' own
        for (OptionIdentifier<?> page : ids(StandardOptions.Pages.class)) {
            assertEquals("impetus", page.getModId(), page.toString());
        }
    }

    @Test
    void theHoldersAreFinalAndCannotBeInstantiated() {
        assertEquals(Set.of(StandardOptions.Group.class, StandardOptions.Pages.class, StandardOptions.Option.class), Set.of(StandardOptions.class.getDeclaredClasses()));
        List<Class<?>> classes = new ArrayList<>(HOLDERS.keySet());
        classes.add(StandardOptions.class);
        for (Class<?> type : classes) {
            String name = type.getName();
            assertTrue(Modifier.isPublic(type.getModifiers()), name);
            assertTrue(Modifier.isFinal(type.getModifiers()), name);
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            assertEquals(1, constructors.length, name);
            assertTrue(Modifier.isPrivate(constructors[0].getModifiers()), name);
            assertEquals(0, constructors[0].getParameterCount(), name);
            assertTrue(Arrays.stream(type.getDeclaredMethods()).noneMatch(m -> Modifier.isPublic(m.getModifiers())), name);
        }
        for (Class<?> holder : HOLDERS.keySet()) {
            assertTrue(Modifier.isStatic(holder.getModifiers()), holder.getName());
            assertEquals(0, Arrays.stream(holder.getDeclaredMethods()).filter(m -> !m.isSynthetic()).count(), holder.getName());
        }
    }

    @Test
    void aTypedIdAModMadeBeforeAHolderLoadedIsAdoptedInsteadOfFailingTheHolder() throws Exception {
        FreshClasses fresh = new FreshClasses(OptionIdentifier.class, StandardOptions.class);
        Class<?> ids = fresh.load(OptionIdentifier.class);
        assertNotSame(OptionIdentifier.class, ids);
        Method create = ids.getMethod("create", String.class, String.class, Class.class);
        Method untyped = ids.getMethod("create", String.class, String.class);
        Method type = ids.getMethod("getType");
        // One pair per holder typed first, plus one made untyped first
        Object vsync = create.invoke(null, "minecraft", "vsync", Boolean.class);
        Object general = create.invoke(null, "impetus", "general", Integer.class);
        Object sorting = create.invoke(null, "impetus", "sorting", String.class);
        Object window = untyped.invoke(null, "minecraft", "window");
        Map<String, Object> options = declared(fresh.initialise(StandardOptions.Option.class));
        Map<String, Object> pages = declared(fresh.initialise(StandardOptions.Pages.class));
        Map<String, Object> groups = declared(fresh.initialise(StandardOptions.Group.class));
        assertSame(vsync, options.get("VSYNC"));
        assertSame(general, pages.get("GENERAL"));
        assertSame(sorting, groups.get("SORTING"));
        assertSame(window, groups.get("WINDOW"));
        // The mod's types are kept and every other id is untyped as usual
        assertSame(Boolean.class, type.invoke(vsync));
        assertSame(Integer.class, type.invoke(general));
        assertSame(String.class, type.invoke(sorting));
        Set<Object> adopted = Set.of(vsync, general, sorting);
        for (Map<String, Object> holder : List.of(options, pages, groups)) {
            for (Map.Entry<String, Object> id : holder.entrySet()) {
                if (!adopted.contains(id.getValue())) {
                    assertSame(void.class, type.invoke(id.getValue()), id.getKey());
                }
            }
        }
        assertEquals(pinned(OPTIONS), names(options));
        assertEquals(pinned(PAGES), names(pages));
        assertEquals(pinned(GROUPS), names(groups));
        // A typed create after the holder loaded is still the mod's own error
        InvocationTargetException late = assertThrows(InvocationTargetException.class, () -> create.invoke(null, "impetus", "shaders", Boolean.class));
        assertInstanceOf(IllegalArgumentException.class, late.getCause());
        assertSame(void.class, type.invoke(pages.get("SHADERS")));
    }

    @Test
    void racingFirstUseStillAgreesOnOneInstancePerPair() throws Exception {
        Map<String, String> pinnedOptions = pinned(OPTIONS);
        for (int round = 0; round < 24; round++) {
            FreshClasses fresh = new FreshClasses(OptionIdentifier.class, StandardOptions.class);
            Class<?> ids = fresh.load(OptionIdentifier.class);
            Method create = ids.getMethod("create", String.class, String.class, Class.class);
            Method untyped = ids.getMethod("create", String.class, String.class);
            int threads = 8;
            CyclicBarrier start = new CyclicBarrier(threads);
            List<Future<Map<String, Object>>> loaders = new ArrayList<>();
            List<Future<Map<String, Object>>> creators = new ArrayList<>();
            Future<Object> typed;
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                for (int i = 0; i < 4; i++) {
                    loaders.add(pool.submit(() -> {
                        start.await();
                        return declared(fresh.initialise(StandardOptions.Option.class));
                    }));
                }
                for (int i = 0; i < 3; i++) {
                    creators.add(pool.submit(() -> {
                        start.await();
                        // Every pair but the one the typed racer may take first
                        Map<String, Object> made = new LinkedHashMap<>();
                        for (Map.Entry<String, String> pair : pinnedOptions.entrySet()) {
                            if (!pair.getKey().equals("VSYNC")) {
                                String[] parts = pair.getValue().split(":");
                                made.put(pair.getKey(), untyped.invoke(null, parts[0], parts[1]));
                            }
                        }
                        return made;
                    }));
                }
                typed = pool.submit(() -> {
                    start.await();
                    try {
                        return create.invoke(null, "minecraft", "vsync", Boolean.class);
                    } catch (InvocationTargetException refused) {
                        return refused.getCause();
                    }
                });
            }
            Map<String, Object> first = loaders.get(0).get();
            for (Future<Map<String, Object>> loader : loaders) {
                Map<String, Object> seen = loader.get();
                assertEquals(first.keySet(), seen.keySet());
                first.forEach((name, id) -> assertSame(id, seen.get(name), name));
            }
            for (Future<Map<String, Object>> creator : creators) {
                creator.get().forEach((name, id) -> assertSame(first.get(name), id, name));
            }
            // Either the mod typed vsync first and the holder took its id, or the holder came first and the mod was refused
            Object outcome = typed.get();
            Object type = ids.getMethod("getType").invoke(first.get("VSYNC"));
            if (outcome instanceof IllegalArgumentException) {
                assertSame(void.class, type);
            } else {
                assertSame(outcome, first.get("VSYNC"));
                assertSame(Boolean.class, type);
            }
            assertEquals(pinnedOptions, names(first));
        }
    }
}
