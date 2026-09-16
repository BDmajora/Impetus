package com.bdmajora.coarctatio.mixin.forge;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLContainer;
import net.minecraftforge.fml.common.InjectedModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

// Every registry name goes through this; a name that already carries a namespace is returned as given without consulting the active mod or logging the "potentially dangerous alternative prefix" warning, which a large pack emits by the thousand (UniversalTweaks, by Darkhax). Every caller passes warnOverrides, so the result matches vanilla's. remap = false, Forge class
@Mixin(value = GameData.class, remap = false)
public abstract class GameDataMixin {
    @Inject(method = "checkPrefix(Ljava/lang/String;Z)Lnet/minecraft/util/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private static void coarctatio$quietPrefixCheck(String name, boolean warnOverrides, CallbackInfoReturnable<ResourceLocation> cir) {
        int separator = name.lastIndexOf(':');
        String namespace = separator == -1 ? "" : name.substring(0, separator).toLowerCase(Locale.ROOT);
        String path = separator == -1 ? name : name.substring(separator + 1);
        if (namespace.isEmpty()) {
            ModContainer activeMod = Loader.instance().activeModContainer();
            namespace = activeMod == null || (activeMod instanceof InjectedModContainer && ((InjectedModContainer) activeMod).wrappedContainer instanceof FMLContainer)
                    ? "minecraft" : activeMod.getModId().toLowerCase(Locale.ROOT);
        }
        cir.setReturnValue(new ResourceLocation(namespace, path));
    }
}
