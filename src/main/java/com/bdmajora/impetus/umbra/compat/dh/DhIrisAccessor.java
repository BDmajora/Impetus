package com.bdmajora.impetus.umbra.compat.dh;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IDependencyInjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Proxy handler that answers the Distant Horizons Iris accessor calls.
public final class DhIrisAccessor implements InvocationHandler {
    // Logger of registration failures.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Name of the Iris accessor interface.
    private static final String IRIS_ACCESSOR = "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor";
    // Class of the Distant Horizons accessor injector.
    private static final String INJECTOR = "com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector";
    // Name of the depth texture class.
    private static final String DEPTH_TEXTURE = "com.seibel.distanthorizons.common.commonMixins.IFramebufferDepthTexture";

    // Class that owns the depth texture.
    private final Class<?> depthTextureOwner;
    // Handle that reads the depth texture.
    private final MethodHandle depthTexture;

    // Looks up the depth texture getter of Distant Horizons framebuffers.
    DhIrisAccessor() {
        Class<?> owner = null;
        MethodHandle getter = null;
        try {
            owner = Class.forName(DEPTH_TEXTURE);
            getter = MethodHandles.lookup().findVirtual(owner, "distantHorizons$getDistantHorizonsDepthTexture",
                    MethodType.methodType(int.class));
        } catch (ReflectiveOperationException e) {
            owner = null;
            LOGGER.warn("[Umbra] Distant Horizons has no framebuffer depth texture interface; its vanilla fade will have no depth to read", e);
        }
        this.depthTextureOwner = owner;
        this.depthTexture = getter;
    }

    // Binds the accessor before Distant Horizons initializes.
    public static void register() {
        DhApi.events.bind(DhApiBeforeDhInitEvent.class, new DhApiBeforeDhInitEvent() {
            @Override
            public void beforeDistantHorizonsInit(DhApiEventParam<Void> input) {
                bind();
            }
        });
    }

    // Installs a proxy of the shader accessor interface with Distant Horizons.
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void bind() {
        try {
            Class<?> accessorType = Class.forName(IRIS_ACCESSOR);
            IDependencyInjector injector = (IDependencyInjector) Class.forName(INJECTOR).getField("INSTANCE").get(null);
            if (injector.get(accessorType) != null) {
                LOGGER.info("[Umbra] Distant Horizons already has a shader mod accessor; leaving it in place");
                return;
            }
            Object accessor = Proxy.newProxyInstance(DhIrisAccessor.class.getClassLoader(),
                    new Class<?>[] {accessorType}, new DhIrisAccessor());
            injector.bind(accessorType, (IBindable) accessor);
            LOGGER.info("[Umbra] Registered with Distant Horizons as its shader mod");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.error("[Umbra] Could not register with Distant Horizons as its shader mod; LODs will draw as though no pack were active", e);
        }
    }

    // Answers a call of the shader accessor interface.
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "getModName":
                return "Impetus";
            case "isShaderPackInUse":
                return Umbra.isShaderPackInUse();
            case "isRenderingShadowPass":
                return UmbraShadowRenderer.isShadowPass();
            case "isReverseZDuringShaders":
                return false;
            case "getFramebufferDepthTextureId":
                return args == null || args.length == 0 ? -1 : getFramebufferDepthTexture(args[0]);
            case "getDelayedSetupComplete":
                return true;
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "Impetus IIrisAccessor";
            default:
                return zeroOf(method.getReturnType());
        }
    }

    // Returns the zero value of a return type or null.
    static Object zeroOf(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    // Returns the depth texture of a framebuffer or minus one.
    private int getFramebufferDepthTexture(Object framebuffer) throws Throwable {
        if (this.depthTexture == null || !this.depthTextureOwner.isInstance(framebuffer)) {
            return -1;
        }
        return (int) this.depthTexture.invoke(framebuffer);
    }
}
