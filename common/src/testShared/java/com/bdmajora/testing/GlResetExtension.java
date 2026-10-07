package com.bdmajora.testing;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Auto-registered through META-INF/services so every test starts from a clean GL mock; a module adds its own window stubs through onReset
public final class GlResetExtension implements BeforeEachCallback {
    private static final List<Runnable> RESETS = new CopyOnWriteArrayList<>();

    public static void onReset(Runnable reset) {
        RESETS.add(reset);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        TestGl.reset();
        RESETS.forEach(Runnable::run);
    }
}
