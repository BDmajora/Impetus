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
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Impetus as DH's IIrisAccessor, the switch DH reads for "a shader pack owns this frame": DH binds one only for Actinium, and without it DH fades, anti-aliases and far-fades over the pack's LOD gbuffer, culls the shadow pass with the camera frustum and picks a far plane dhProjection does not match. The interface is DH core rather than API, so it is answered by a Proxy; only ever loaded through DhCompat's handle, since it imports the DH API
public final class DhIrisAccessor implements InvocationHandler {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    private static final String IRIS_ACCESSOR = "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor";
    private static final String INJECTOR = "com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector";
    // Put on MC's Framebuffer by DH's depth-texture mixin, which ImpetusMixinRegistrar re-enables
    private static final String DEPTH_TEXTURE = "com.seibel.distanthorizons.common.commonMixins.IFramebufferDepthTexture";

    private final Class<?> depthTextureOwner;
    private final MethodHandle depthTexture;

    // Resolves DH's framebuffer depth getter; without it every framebuffer answers -1, as DH's own Iris accessor does for one it never patched
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

    // Construction hook: DH reads its mod accessors once, in its own init, which runs before Impetus's; its before-init event fires just ahead of that read
    public static void register() {
        DhApi.events.bind(DhApiBeforeDhInitEvent.class, new DhApiBeforeDhInitEvent() {
            @Override
            public void beforeDistantHorizonsInit(DhApiEventParam<Void> input) {
                bind();
            }
        });
    }

    // Binds the accessor into DH's injector unless one is already there (Actinium's, or a DH that learned about Impetus), since a second binding throws
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
                return getFramebufferDepthTexture(args[0]);
            case "getDelayedSetupComplete":
                return true;
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "Impetus IIrisAccessor";
            default:
                // finishDelayedSetup, and anything a later DH adds, answers its type's zero value rather than throwing on DH's render thread
                Class<?> type = method.getReturnType();
                return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
        }
    }

    // DH's depth texture on MC's framebuffer, which its fade pass samples; -1 when DH's mixin did not patch this framebuffer
    private int getFramebufferDepthTexture(Object framebuffer) throws Throwable {
        if (this.depthTexture == null || !this.depthTextureOwner.isInstance(framebuffer)) {
            return -1;
        }
        return (int) this.depthTexture.invoke(framebuffer);
    }
}
