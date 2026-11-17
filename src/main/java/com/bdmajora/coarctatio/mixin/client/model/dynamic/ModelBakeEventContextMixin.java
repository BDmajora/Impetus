package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.BakeEventDispatcher;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes ModelBakeEvent.
@Mixin(value = ModelBakeEvent.class, remap = false)
public abstract class ModelBakeEventContextMixin implements IContextSetter, BakeEventDispatcher.ContextAwareBakeEvent {
    // Mod that was active before the event.
    private ModContainer coarctatio$lastMod;

    // Remembers the mod whose bake listener runs.
    @Override
    public void setModContainer(ModContainer mod) {
        this.coarctatio$lastMod = mod;
    }

    // Returns the mod whose bake listener ran last.
    @Override
    public ModContainer coarctatio$lastMod() {
        return this.coarctatio$lastMod;
    }
}
