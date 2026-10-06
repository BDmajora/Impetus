package com.bdmajora.testing;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.ExceptionMethod;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.platform.launcher.LauncherSession;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.junit.platform.launcher.LauncherSessionListener;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

// Gives a game class the accessor interface a mixin would have added to it, so production code that casts to that
// interface runs against real instances; only needed for final classes, since Mockito can add interfaces to the rest.
// Runs as a launcher session listener because the transformation has to be installed before anything loads the target
public final class Splice implements LauncherSessionListener {
    // Target class name -> the interface spliced onto it; every method of the interface must be a plain accessor
    private static final Map<String, Class<?>> TARGETS = new LinkedHashMap<>();

    // Interfaces whose methods are answered by a helper rather than by a generated field
    private static final Map<Class<?>, Class<?>> DELEGATES = new LinkedHashMap<>();

    // Interfaces whose default methods are the point of the splice: only the abstract ones get a body, and it does nothing
    private static final java.util.Set<Class<?>> DEFAULTS_KEPT = new java.util.LinkedHashSet<>();

    // Setters whose field is named by a getter of another name, so the pair shares one field
    private static final Map<String, String> FIELD_ALIASES = new LinkedHashMap<>();

    // Getters a mixin implements by hand over a field the target already has, answered from that field
    private static final Map<String, String> REAL_FIELDS = new LinkedHashMap<>();

    static {
        DELEGATES.put(com.bdmajora.fulgor.api.LightInfoBlock.class, SplicedAnswers.class);

        TARGETS.put("net.minecraft.block.Block", com.bdmajora.fulgor.api.LightInfoBlock.class);
        TARGETS.put("net.minecraft.item.ItemStack", com.bdmajora.equilibrium.common.advancements.PreviousStackSize.class);
        TARGETS.put("net.minecraft.world.chunk.storage.ExtendedBlockStorage",
                com.bdmajora.equilibrium.mixin.worldgen.chunk_copy.ExtendedBlockStorageAccessor.class);

        // The recycled Forge events; every refresh is a default method the real mixins override per event class
        DEFAULTS_KEPT.add(com.bdmajora.coarctatio.events.RecyclableEvent.class);
        TARGETS.put("net.minecraftforge.fml.common.gameevent.TickEvent",
                com.bdmajora.coarctatio.events.RecyclableEvent.class);
        TARGETS.put("net.minecraftforge.event.AttachCapabilitiesEvent",
                com.bdmajora.coarctatio.events.RecyclableEvent.class);
        TARGETS.put("net.minecraftforge.event.world.BlockEvent",
                com.bdmajora.coarctatio.events.RecyclableEvent.class);

        // The resource managers and packs the dynamic model loader lists paths from
        TARGETS.put("net.minecraft.client.resources.SimpleReloadableResourceManager",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.SimpleReloadableResourceManagerAccessor.class);
        TARGETS.put("net.minecraft.client.resources.FallbackResourceManager",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.FallbackResourceManagerAccessor.class);
        TARGETS.put("net.minecraft.client.resources.AbstractResourcePack",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.AbstractResourcePackAccessor.class);
        TARGETS.put("net.minecraft.client.resources.LegacyV2Adapter",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.LegacyV2AdapterAccessor.class);
        TARGETS.put("net.minecraft.client.resources.FileResourcePack",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.FileResourcePackAccessor.class);

        // The model bakery's item variant table, read by the dynamic loader without running a bake
        TARGETS.put("net.minecraft.client.renderer.block.model.ModelBakery",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.ModelBakeryAccessor.class);

        // The bus id the bake dispatcher selects listeners by, and the owning mod the bus names on each bake event
        TARGETS.put("net.minecraftforge.fml.common.eventhandler.EventBus",
                com.bdmajora.coarctatio.mixin.client.model.dynamic.EventBusAccessor.class);
        TARGETS.put("net.minecraftforge.client.event.ModelBakeEvent", BakeEventContext.class);
        FIELD_ALIASES.put("setModContainer", "splice$lastMod");

        // A model box's quads, which the block entity baker walks
        TARGETS.put("net.minecraft.client.model.ModelBox", com.bdmajora.extras.client.bakedentities.ModelBoxAccess.class);
        REAL_FIELDS.put("impetus$getQuads", "quadList");
    }

    // The two interfaces ModelBakeEvent's context mixin adds, as one so the registry keeps one interface per target
    public interface BakeEventContext extends net.minecraftforge.fml.common.eventhandler.IContextSetter,
            com.bdmajora.coarctatio.client.model.dynamic.BakeEventDispatcher.ContextAwareBakeEvent {}

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        install();
    }

    private static boolean installed;

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        // Cleanroom's launcher records the side before anything asks; FMLLaunchHandler's initialiser reads it, and the client is the side every suite runs as
        com.cleanroommc.common.CleanroomEnvironment.setSide(net.minecraftforge.fml.relauncher.Side.CLIENT);
        ByteBuddyAgent.install();
        // The interfaces are spliced first, since preparing the constructors below loads some of the same classes
        AgentBuilder agent = new AgentBuilder.Default();
        for (Map.Entry<String, Class<?>> target : TARGETS.entrySet()) {
            Class<?> added = target.getValue();
            agent = agent.type(ElementMatchers.named(target.getKey()))
                    .transform((builder, type, loader, module, pd) -> implement(builder, added));
        }
        agent.installOn(ByteBuddyAgent.getInstrumentation());
        MixinSubclassing subclassing = new MixinSubclassing();
        ByteBuddyAgent.getInstrumentation().addTransformer(subclassing);
        // Every spliced interface's signature is resolved here, on this thread: the splice reads it while
        // transforming its target, and a class first loaded from inside a transformer is handed to no transformer
        // at all, so it would miss both the splice and the constructors added below
        for (Class<?> added : new java.util.LinkedHashSet<>(TARGETS.values())) {
            for (Method method : added.getMethods()) {
                // The generic signatures too: ByteBuddy reads them while transforming, and a type first loaded
                // from inside a transformer is handed to no transformer at all
                method.getGenericReturnType();
                method.getGenericParameterTypes();
            }
        }
        // The targets themselves for the same reason: one first loaded from inside a transformer would never be
        // handed to this agent, and would quietly not implement the interface production code casts it to
        for (String target : TARGETS.keySet()) {
            try {
                Class.forName(target, false, Splice.class.getClassLoader());
            } catch (Throwable ignored) {
                // A target that cannot be loaded here is spliced when something else loads it
            }
        }
        subclassing.prepare();
    }

    // An @Accessor names a field the target already has; everything else gets one generated field per getter/setter
    // pair, and anything that is not a plain accessor throws if it is ever called
    private static <T> DynamicType.Builder<T> implement(DynamicType.Builder<T> builder, Class<?> added) {
        DynamicType.Builder<T> result = builder.implement(added);
        if (DEFAULTS_KEPT.contains(added)) {
            for (Method method : added.getMethods()) {
                if (method.isDefault() || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                result = result.method(ElementMatchers.is(method)).intercept(net.bytebuddy.implementation.StubMethod.INSTANCE);
            }
            return result;
        }
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        for (Method method : added.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (isGetter(method) && method.getAnnotation(Accessor.class) == null && !DELEGATES.containsKey(added)
                    && !REAL_FIELDS.containsKey(method.getName())) {
                fields.put(field(method), method.getReturnType());
            }
        }
        for (Map.Entry<String, Class<?>> field : fields.entrySet()) {
            result = result.defineField(field.getKey(), field.getValue());
        }
        for (Method method : added.getMethods()) {
            // A static accessor is rewritten at its call sites by MixinSubclassing; there is nothing to override here
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            MethodDescription.Token token = new MethodDescription.ForLoadedMethod(method).asToken(ElementMatchers.none());
            Accessor accessor = method.getAnnotation(Accessor.class);
            Class<?> delegate = DELEGATES.get(added);
            if (delegate != null) {
                result = result.method(ElementMatchers.is(method)).intercept(MethodDelegation
                        .withDefaultConfiguration().filter(ElementMatchers.named(method.getName())).to(delegate));
            } else if (accessor != null) {
                result = result.method(ElementMatchers.is(method)).intercept(FieldAccessor.ofField(accessor.value()));
            } else if (REAL_FIELDS.containsKey(method.getName())) {
                result = result.method(ElementMatchers.is(method)).intercept(FieldAccessor.ofField(REAL_FIELDS.get(method.getName())));
            } else if (method.getAnnotation(org.spongepowered.asm.mixin.gen.Invoker.class) != null) {
                // An @Invoker names a method the target already has, so the splice just forwards to it
                result = result.method(ElementMatchers.is(method)).intercept(net.bytebuddy.implementation.MethodCall
                        .invoke(ElementMatchers.named(method.getAnnotation(org.spongepowered.asm.mixin.gen.Invoker.class).value())
                                .and(ElementMatchers.takesArguments(method.getParameterTypes())))
                        .withAllArguments());
            } else if (isGetter(method) || isSetter(method, fields)) {
                result = result.method(ElementMatchers.is(method)).intercept(FieldAccessor.ofField(field(method)));
            } else {
                result = result.method(ElementMatchers.is(method))
                        .intercept(ExceptionMethod.throwing(UnsupportedOperationException.class, token.getName() + " is not a plain accessor"));
            }
        }
        return result;
    }

    private static boolean isGetter(Method method) {
        return method.getParameterCount() == 0 && method.getReturnType() != void.class;
    }

    private static boolean isSetter(Method method, Map<String, Class<?>> fields) {
        return method.getParameterCount() == 1 && method.getReturnType() == void.class && fields.containsKey(field(method));
    }

    // equilibrium$previousCount and equilibrium$setPreviousCount both name the field splice$previousCount
    private static String field(Method method) {
        String name = method.getName();
        String alias = FIELD_ALIASES.get(name);
        if (alias != null) {
            return alias;
        }
        int dollar = name.indexOf('$');
        String base = dollar < 0 ? name : name.substring(dollar + 1);
        for (String prefix : new String[] {"set", "get", "is"}) {
            if (base.length() > prefix.length() && base.startsWith(prefix) && Character.isUpperCase(base.charAt(prefix.length()))) {
                base = Character.toLowerCase(base.charAt(prefix.length())) + base.substring(prefix.length() + 1);
                break;
            }
        }
        return "splice$" + base;
    }
}
