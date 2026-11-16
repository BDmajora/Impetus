package com.bdmajora.coarctatio.mixin.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.IRegistryDelegate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

// Mixin that changes ItemStack.
@Mixin(ItemStack.class)
public abstract class ItemStackCapabilityMixin {
    // Reference to the field is empty of the target.
    @Shadow
    private boolean isEmpty;

    // Reference to the field capabilities of the target.
    @Shadow(remap = false)
    private CapabilityDispatcher capabilities;

    // Reference to the field cap nbt of the target.
    @Shadow(remap = false)
    private NBTTagCompound capNBT;

    // Reference to the field delegate of the target.
    @Shadow(remap = false)
    private IRegistryDelegate<Item> delegate;

    // Reference to the method get item raw of the target.
    @Shadow(remap = false)
    @Nullable
    protected abstract Item getItemRaw();

    // Capabilities ready.
    private boolean coarctatio$capabilitiesReady;

    // Replaces the whole method forgeInit.
    @Overwrite(remap = false)
    private void forgeInit() {
        Item item = this.getItemRaw();
        if (item != null) {
            this.delegate = item.delegate;
        }
    }

    // Initializes the capabilities.
    private void coarctatio$initCapabilities() {
        if (this.coarctatio$capabilitiesReady) {
            return;
        }
        this.coarctatio$capabilitiesReady = true;
        Item item = this.getItemRaw();
        if (item != null) {
            ItemStack self = (ItemStack) (Object) this;
            this.capabilities = ForgeEventFactory.gatherCapabilities(self, item.initCapabilities(self, this.capNBT));
            if (this.capabilities != null && this.capNBT != null) {
                this.capabilities.deserializeNBT(this.capNBT);
            }
        }
    }

    // Replaces the whole method hasCapability.
    @Overwrite(remap = false)
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (this.isEmpty) {
            return false;
        }
        this.coarctatio$initCapabilities();
        return this.capabilities != null && this.capabilities.hasCapability(capability, facing);
    }

    // Replaces the whole method getCapability.
    @Nullable
    @Overwrite(remap = false)
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (this.isEmpty) {
            return null;
        }
        this.coarctatio$initCapabilities();
        return this.capabilities == null ? null : this.capabilities.getCapability(capability, facing);
    }

    // Replaces the whole method areCapsCompatible.
    @Overwrite(remap = false)
    public boolean areCapsCompatible(ItemStack other) {
        this.coarctatio$initCapabilities();
        ItemStackCapabilityMixin that = (ItemStackCapabilityMixin) (Object) other;
        that.coarctatio$initCapabilities();
        if (this.capabilities == null) {
            return that.capabilities == null || that.capabilities.areCompatible(null);
        }
        return this.capabilities.areCompatible(that.capabilities);
    }

    // Runs before every return of copy.
    @Inject(method = "copy", at = @At("RETURN"))
    private void coarctatio$copyUnrealisedCaps(CallbackInfoReturnable<ItemStack> cir) {
        if (!this.coarctatio$capabilitiesReady && this.capNBT != null) {
            ItemStackCapabilityMixin copy = (ItemStackCapabilityMixin) (Object) cir.getReturnValue();
            if (copy.capNBT == null) {
                copy.capNBT = this.capNBT.copy();
            }
        }
    }

    // Runs before every return of writeToNBT.
    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void coarctatio$writeUnrealisedCaps(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (!this.coarctatio$capabilitiesReady && this.capNBT != null && !this.capNBT.isEmpty() && !nbt.hasKey("ForgeCaps")) {
            nbt.setTag("ForgeCaps", this.capNBT);
        }
    }
}
