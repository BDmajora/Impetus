package com.bdmajora.impetus.lwjgl;

// Holds the one LWJGLService, built at class init; separate from the record so a test backend can be mocked from it without a class-init cycle
public final class LWJGLServiceProvider {
    public static final LWJGLService LWJGL = createInstance();

    private LWJGLServiceProvider() {}

    // Unit tests name a factory class through impetus.lwjgl.service, since the real service needs a GL context
    static LWJGLService createInstance() {
        String override = System.getProperty("impetus.lwjgl.service");
        if (override == null) {
            return LWJGLService.create();
        }
        try {
            return (LWJGLService) Class.forName(override).getDeclaredMethod("create").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
