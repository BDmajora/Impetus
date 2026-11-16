package com.bdmajora.coarctatio.state;

import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Packed block state that carries unlisted properties.
public class CoarctatioExtendedBlockState extends CoarctatioBlockState implements IExtendedBlockState {
    // Unlisted properties and their values.
    private final ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlistedProperties;
    // Whether an unlisted value is set.
    private final boolean dirty;

    // Creates a state with unlisted properties.
    public CoarctatioExtendedBlockState(PropertyValueMapper mapper, Block block,
                                       ImmutableMap<IProperty<?>, Comparable<?>> properties,
                                       ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlistedProperties,
                                       boolean dirty) {
        super(mapper, block, properties);
        this.unlistedProperties = unlistedProperties;
        this.dirty = dirty;
    }

    // Creates a state with a known packed value.
    private CoarctatioExtendedBlockState(PropertyValueMapper mapper, Block block,
                                        ImmutableMap<IProperty<?>, Comparable<?>> properties,
                                        ImmutableMap<IUnlistedProperty<?>, Optional<?>> unlistedProperties,
                                        boolean dirty, int value) {
        super(mapper, block, properties, value);
        this.unlistedProperties = unlistedProperties;
        this.dirty = dirty;
    }

    // Returns the state with the listed property set and the unlisted values kept.
    @Override
    public <T extends Comparable<T>, V extends T> IBlockState withProperty(IProperty<T> property, V newValue) {
        IBlockState clean = super.withProperty(property, newValue);

        if (!this.dirty || clean == this) {
            return clean;
        }

        return new CoarctatioExtendedBlockState(this.mapper, getBlock(), clean.getProperties(),
                this.unlistedProperties, true, ((CoarctatioBlockState) clean).value);
    }

    // Returns the state with the unlisted property set and rejects an unknown or invalid value.
    @Override
    public <V> IExtendedBlockState withProperty(IUnlistedProperty<V> property, V newValue) {
        Optional<?> current = this.unlistedProperties.get(property);

        if (current == null) {
            throw new IllegalArgumentException("Cannot set unlisted property " + property
                    + " as it does not exist in " + getBlock().getBlockState());
        }

        if (Objects.equals(current.orElse(null), newValue)) {
            return this;
        }

        if (!property.isValid(newValue)) {
            throw new IllegalArgumentException("Cannot set unlisted property " + property + " to " + newValue
                    + " on block " + Block.REGISTRY.getNameForObject(getBlock()) + ", it is not an allowed value");
        }

        boolean anyPresent = false;
        ImmutableMap.Builder<IUnlistedProperty<?>, Optional<?>> builder = ImmutableMap.builder();

        for (Map.Entry<IUnlistedProperty<?>, Optional<?>> entry : this.unlistedProperties.entrySet()) {
            IUnlistedProperty<?> key = entry.getKey();
            Optional<?> value = key.equals(property) ? Optional.ofNullable(newValue) : entry.getValue();

            anyPresent |= value.isPresent();
            builder.put(key, value);
        }

        if (!anyPresent) {
            return (IExtendedBlockState) getClean();
        }

        return new CoarctatioExtendedBlockState(this.mapper, getBlock(), getProperties(),
                builder.build(), true, this.value);
    }

    // Returns the unlisted property keys.
    @Override
    public Collection<IUnlistedProperty<?>> getUnlistedNames() {
        return this.unlistedProperties.keySet();
    }

    // Returns the unlisted value and rejects an unknown property.
    @Override
    public <V> V getValue(IUnlistedProperty<V> property) {
        Optional<?> value = this.unlistedProperties.get(property);

        if (value == null) {
            throw new IllegalArgumentException("Cannot get unlisted property " + property
                    + " as it does not exist in " + getBlock().getBlockState());
        }

        return property.getType().cast(value.orElse(null));
    }

    // Returns the unlisted properties and their values.
    @Override
    public ImmutableMap<IUnlistedProperty<?>, Optional<?>> getUnlistedProperties() {
        return this.unlistedProperties;
    }

    // Returns the state without unlisted values.
    @Override
    public IBlockState getClean() {
        return this.dirty ? this.mapper.byValue(this.value) : this;
    }
}
