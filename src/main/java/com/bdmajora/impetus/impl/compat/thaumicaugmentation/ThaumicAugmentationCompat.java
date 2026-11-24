package com.bdmajora.impetus.impl.compat.thaumicaugmentation;

import com.bdmajora.impetus.impl.compat.OptionalHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

// Thaumic Augmentation drains its shader TESR queue from a hook it ASMs in before postRenderDamagedBlocks at the end of renderEntities.
// Impetus cancels renderEntities once its own block entity pass is done, so that hook never ran: starfield glass, obelisks and rift barriers
// lost their rift/fracture/mirror pass, and every frame each of them appended one more callback to a list nothing cleared,
// a leak that kept growing the heap and lengthening GC pauses the longer the player stayed near them.
public final class ThaumicAugmentationCompat {
    private static final Logger LOGGER = LogManager.getLogger("Impetus");

    static final String HOOKS = "thecodex6824.thaumicaugmentation.client.internal.TAHooksClient";
    static final String METHOD = "onRenderEntities";

    // Null when TA is absent or the hook failed once; volatile only for the one off disable.
    private static volatile MethodHandle hook = bind(ThaumicAugmentationCompat.class.getClassLoader());

    private ThaumicAugmentationCompat() {
    }

    // Resolves TAHooksClient.onRenderEntities(int) without loading TA when it is not installed.
    static MethodHandle bind(ClassLoader loader) {
        return OptionalHooks.bind(loader, HOOKS, METHOD, MethodType.methodType(void.class, int.class));
    }

    // Runs TA's end of renderEntities hook for the pass, where vanilla would have run it: after block entities, lightmap still on.
    public static void onRenderEntities(int pass) {
        MethodHandle handle = hook;
        if (handle == null) {
            return;
        }
        try {
            handle.invokeExact(pass);
        } catch (Throwable t) {
            // One failure turns the hook off instead of throwing out of every frame.
            hook = null;
            LOGGER.error("[Impetus] Thaumic Augmentation's render hook failed and was turned off", t);
        }
    }

    static boolean isBound() {
        return hook != null;
    }
}
