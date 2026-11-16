package com.bdmajora.impetus.lwjgl;

// Holds the one LWJGL service.
public final class LWJGLServiceProvider {
    // The service.
    public static final LWJGLService LWJGL = createInstance();

    // Prevents instantiation
    private LWJGLServiceProvider() {}

    // Creates the service or the service of the factory class named by the system property.
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
