package net.irisshaders.iris.api.v0;

// Test stand-in for the shader API contract ShaderModBridge probes reflectively; the real one lives in the root project
public final class IrisApi {
    private static final IrisApi INSTANCE = new IrisApi();
    public static boolean shadersInUse;
    public static Object lastParent;
    public static Object screenToReturn = "screen";

    public static IrisApi getInstance() {
        return INSTANCE;
    }

    public boolean isShaderPackInUse() {
        return shadersInUse;
    }

    public Object openMainIrisScreenObj(Object parent) {
        lastParent = parent;
        return screenToReturn;
    }
}
