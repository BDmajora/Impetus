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

// Forge fires AttachCapabilitiesEvent and builds a CapabilityDispatcher for every ItemStack constructed, and a modded game constructs them by the million (recipe matching, JEI, inventory copies, rendering); nearly all are never asked for a capability (Chibi's delayItemStackCapabilityInit, PrototypeTrousers' idea). The dispatcher is built on the first capability query instead; until then the loaded capability NBT is carried as-is so copying and saving lose nothing
@Mixin(ItemStack.class)
public abstract class ItemStackCapabilityMixin {
    @Shadow
    private boolean isEmpty;

    @Shadow(remap = false)
    private CapabilityDispatcher capabilities;

    @Shadow(remap = false)
    private NBTTagCompound capNBT;

    @Shadow(remap = false)
    private IRegistryDelegate<Item> delegate;

    @Shadow(remap = false)
    @Nullable
    protected abstract Item getItemRaw();

    private boolean coarctatio$capabilitiesReady;

    // Forge's constructor hook: only the registry delegate is resolved now, the dispatcher waits
    @Overwrite(remap = false)
    private void forgeInit() {
        Item item = this.getItemRaw();
        if (item != null) {
            this.delegate = item.delegate;
        }
    }

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

    @Overwrite(remap = false)
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (this.isEmpty) {
            return false;
        }
        this.coarctatio$initCapabilities();
        return this.capabilities != null && this.capabilities.hasCapability(capability, facing);
    }

    @Nullable
    @Overwrite(remap = false)
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (this.isEmpty) {
            return null;
        }
        this.coarctatio$initCapabilities();
        return this.capabilities == null ? null : this.capabilities.getCapability(capability, facing);
    }

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

    // Forge only writes ForgeCaps from a live dispatcher; a stack that loaded caps and was never queried still has them in capNBT and must not drop them on save
    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void coarctatio$writeUnrealisedCaps(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (!this.coarctatio$capabilitiesReady && this.capNBT != null && !this.capNBT.isEmpty() && !nbt.hasKey("ForgeCaps")) {
            nbt.setTag("ForgeCaps", this.capNBT);
        }
    }
}
