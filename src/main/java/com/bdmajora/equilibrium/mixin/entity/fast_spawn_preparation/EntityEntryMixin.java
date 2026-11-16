package com.bdmajora.equilibrium.mixin.entity.fast_spawn_preparation;

import com.bdmajora.equilibrium.Equilibrium;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.function.Function;

// Mixin that changes EntityEntry.
@Mixin(value = EntityEntry.class, remap = false)
public abstract class EntityEntryMixin {
    @Shadow
    private Class<? extends Entity> cls;

    @Shadow
    Function<World, ? extends Entity> factory;

    // Runs at the start of init and can cancel it.
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void equilibrium$spinConstructorLambda(CallbackInfo ci) {
        if (this.cls == null || Modifier.isAbstract(this.cls.getModifiers())) {
            return;
        }
        try {
            MethodHandles.Lookup lookup = privateLookup(this.cls);
            MethodHandle ctor = lookup.findConstructor(this.cls, MethodType.methodType(void.class, World.class));
            CallSite site = LambdaMetafactory.metafactory(lookup, "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),
                    ctor,
                    MethodType.methodType(this.cls, World.class));
            @SuppressWarnings("unchecked")
            Function<World, ? extends Entity> spun = (Function<World, ? extends Entity>) site.getTarget().invoke();
            this.factory = spun;
            ci.cancel();
        } catch (Throwable t) {
            Equilibrium.LOGGER.debug("Keeping Forge's reflective factory for {}: {}", this.cls.getName(), t.toString());
        }
    }

    // Handles private lookup.
    private static MethodHandles.Lookup privateLookup(Class<?> target) throws ReflectiveOperationException {
        try {
            return MethodHandles.privateLookupIn(target, MethodHandles.lookup());
        } catch (IllegalAccessException | RuntimeException e) {
            return MethodHandles.lookup();
        }
    }
}
