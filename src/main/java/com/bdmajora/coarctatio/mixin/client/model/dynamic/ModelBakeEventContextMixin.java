package com.bdmajora.coarctatio.mixin.client.model.dynamic;

import com.bdmajora.coarctatio.client.model.dynamic.BakeEventDispatcher;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import org.spongepowered.asm.mixin.Mixin;

// A context-setting event has each listener's owning mod set before it is invoked, which is what lets the registry view scope its keys per mod. remap = false, Forge class
@Mixin(value = ModelBakeEvent.class, remap = false)
public abstract class ModelBakeEventContextMixin implements IContextSetter, BakeEventDispatcher.ContextAwareBakeEvent {
    private ModContainer coarctatio$lastMod;

    @Override
    public void setModContainer(ModContainer mod) {
        this.coarctatio$lastMod = mod;
    }

    @Override
    public ModContainer coarctatio$lastMod() {
        return this.coarctatio$lastMod;
    }
}
