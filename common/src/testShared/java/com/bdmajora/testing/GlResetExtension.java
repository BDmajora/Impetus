package com.bdmajora.testing;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Resets the GL mock per test.
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
