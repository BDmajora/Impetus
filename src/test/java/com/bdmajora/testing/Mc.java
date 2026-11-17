package com.bdmajora.testing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.init.Bootstrap;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

// Minecraft fixtures for the root tests.
public final class Mc {
    private Mc() {}

    // Registers vanilla content once.
    public static void bootstrap() {
        Bootstrap.register();
    }

    // A Mockito mock of a game class that also implements the interfaces a mixin would have added to it
    @SuppressWarnings("unchecked")
    public static <T> T mock(Class<T> type, Class<?>... added) {
        return added.length == 0 ? Mockito.mock(type) : Mockito.mock(type, Mockito.withSettings().extraInterfaces(added));
    }

    // Returns a spy with the added interfaces.
    public static <T> T spy(Class<T> type, Class<?>... added) {
        return added.length == 0 ? Mockito.mock(type, Mockito.CALLS_REAL_METHODS)
                : Mockito.mock(type, Mockito.withSettings().extraInterfaces(added).defaultAnswer(Mockito.CALLS_REAL_METHODS));
    }

    // Installs a mock client.
    public static net.minecraft.client.Minecraft client() {
        net.minecraft.client.Minecraft client = Mockito.mock(net.minecraft.client.Minecraft.class);
        // Stubs the resource manager.
        Mockito.when(client.getResourceManager())
                .thenReturn(Mockito.mock(net.minecraft.client.resources.IReloadableResourceManager.class));
        Statics.set(net.minecraft.client.Minecraft.class, "instance", client);
        return client;
    }

    // Stubs GL and the client settings for TextureUtil.
    public static void textures() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft() == null ? client() : net.minecraft.client.Minecraft.getMinecraft();
        if (Mixins.get(client, "gameSettings") == null) {
            Mixins.set(client, "gameSettings", uninitialized(net.minecraft.client.settings.GameSettings.class));
        }
        if (texturesReady) {
            return;
        }
        texturesReady = true;
        try (org.mockito.MockedStatic<org.lwjgl.opengl.GL11> gl = Mockito.mockStatic(org.lwjgl.opengl.GL11.class)) {
            Class.forName("net.minecraft.client.renderer.texture.TextureUtil");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean texturesReady;

    // Makes Loader.isModLoaded answerable.
    public static void forge(String... loaded) {
        Map<String, net.minecraftforge.fml.common.ModContainer> mods = new HashMap<>();
        for (String id : loaded) {
            mods.put(id, Mockito.mock(net.minecraftforge.fml.common.ModContainer.class));
        }
        Mixins.set(net.minecraftforge.fml.common.Loader.instance(), "namedMods", mods);
        // Sets a mock mod controller.
        Mixins.set(net.minecraftforge.fml.common.Loader.instance(), "modController",
                Mockito.mock(net.minecraftforge.fml.common.LoadController.class));
    }

    // Returns a mock whose mixin interface methods run on real mixin instances.
    @SuppressWarnings("unchecked")
    public static <T> T backed(Class<T> type, Object... mixins) {
        List<Class<?>> interfaces = new ArrayList<>();
        for (Object mixin : mixins) {
            // Walks the mixin's class hierarchy.
            for (Class<?> c = mixin.getClass(); c != null; c = c.getSuperclass()) {
                for (Class<?> added : c.getInterfaces()) {
                    if (added.getName().startsWith("com.bdmajora.") && !interfaces.contains(added)) {
                        interfaces.add(added);
                    }
                }
            }
        }
        return Mockito.mock(type, Mockito.withSettings()
                .extraInterfaces(interfaces.toArray(new Class<?>[0]))
                .defaultAnswer(invocation -> {
                    for (Object mixin : mixins) {
                        Class<?> owner = invocation.getMethod().getDeclaringClass();
                        if (owner.isInterface() && owner.isInstance(mixin)) {
                            return invocation.getMethod().invoke(mixin, invocation.getArguments());
                        }
                    }
                    return Mockito.RETURNS_DEFAULTS.answer(invocation);
                }));
    }

    // Returns an instance created without a constructor.
    public static <T> T uninitialized(Class<T> type) {
        return Statics.allocate(type);
    }

    // A @Share/@Local reference holding one value
    public static <T> LocalRef<T> ref(T initial) {
        return new LocalRef<T>() {
            private T value = initial;

            @Override
            public T get() {
                return value;
            }

            @Override
            public void set(T value) {
                this.value = value;
            }
        };
    }

    // A @Share/@Local boolean reference
    public static LocalBooleanRef booleanRef(boolean initial) {
        return new LocalBooleanRef() {
            private boolean value = initial;

            @Override
            public boolean get() {
                return value;
            }

            @Override
            public void set(boolean value) {
                this.value = value;
            }
        };
    }

    // A @WrapOperation original that records the arguments it was called with and answers a fixed value
    public static final class Recorded<R> implements Operation<R> {
        public final List<Object[]> calls = new ArrayList<>();
        private final R result;

        public Recorded(R result) {
            this.result = result;
        }

        @Override
        public R call(Object... args) {
            calls.add(args);
            return result;
        }

        public int count() {
            return calls.size();
        }

        public Object[] last() {
            return calls.get(calls.size() - 1);
        }

        @Override
        public String toString() {
            return calls.stream().map(Arrays::toString).toList().toString();
        }
    }

    public static <R> Recorded<R> operation(R result) {
        return new Recorded<>(result);
    }

    public static Recorded<Void> operation() {
        return new Recorded<>(null);
    }
}
