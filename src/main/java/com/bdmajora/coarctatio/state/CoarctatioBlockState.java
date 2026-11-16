package com.bdmajora.coarctatio.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;

import java.util.Map;

// Block state that finds its neighbors through packed property indexes.
public class CoarctatioBlockState extends BlockStateContainer.StateImplementation {
    // Mapper shared by every state of the block.
    protected final PropertyValueMapper mapper;

    // Packed property indexes of this state.
    protected int value;

    // Creates a state with compacted properties.
    public CoarctatioBlockState(PropertyValueMapper mapper, Block block,
                               ImmutableMap<IProperty<?>, Comparable<?>> properties) {
        super(block, CompactPropertyMaps.compact(mapper.sharedKeys(properties), properties));
        this.mapper = mapper;
    }

    // Creates a state with a known packed value.
    protected CoarctatioBlockState(PropertyValueMapper mapper, Block block,
                                  ImmutableMap<IProperty<?>, Comparable<?>> properties, int value) {
        super(block, properties);
        this.mapper = mapper;
        this.value = value;
    }

    // Registers the state with the mapper and stores its packed value.
    @Override
    public void buildPropertyValueTable(
            Map<Map<IProperty<?>, Comparable<?>>, BlockStateContainer.StateImplementation> map) {
        this.value = this.mapper.register(this);
    }

    // Returns the state with the property set and rejects an unknown property or value.
    @Override
    public <T extends Comparable<T>, V extends T> IBlockState withProperty(IProperty<T> property, V newValue) {
        int packed = this.mapper.withValue(this.value, property, newValue);

        if (packed == this.value) {
            return this;
        }

        if (packed < 0) {
            throw describeFailure(property, newValue);
        }

        IBlockState state = this.mapper.byValue(packed);

        if (state == null) {
            throw new IllegalStateException("Impetus/Coarctatio: no state registered for "
                    + Block.REGISTRY.getNameForObject(getBlock()) + " at packed index " + packed
                    + " while setting " + property + " to " + newValue
                    + ". Please report this with the mod list.");
        }

        return state;
    }

    // Returns the table of neighbors and builds it on first use.
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ImmutableTable<IProperty<?>, Comparable<?>, IBlockState> getPropertyValueTable() {
        ImmutableTable<IProperty<?>, Comparable<?>, IBlockState> table = this.propertyValueTable;

        if (table != null) {
            return table;
        }

        PropertyValueMapper.TABLES_MATERIALISED.incrementAndGet();

        ImmutableTable.Builder<IProperty<?>, Comparable<?>, IBlockState> builder = ImmutableTable.builder();

        for (Map.Entry<IProperty<?>, Comparable<?>> entry : getProperties().entrySet()) {
            IProperty<?> property = entry.getKey();

            for (Comparable<?> allowed : (Iterable<Comparable<?>>) (Iterable) property.getAllowedValues()) {
                if (allowed == entry.getValue()) {
                    continue;
                }

                int packed = this.mapper.withValue(this.value, property, allowed);

                if (packed >= 0) {
                    IBlockState state = this.mapper.byValue(packed);

                    if (state != null) {
                        builder.put(property, allowed, state);
                    }
                }
            }
        }

        table = builder.build();
        this.propertyValueTable = table;

        return table;
    }

    // Returns the error that explains why the property could not be set.
    private IllegalArgumentException describeFailure(IProperty<?> property, Object newValue) {
        if (!getProperties().containsKey(property)) {
            return new IllegalArgumentException("Cannot set property " + property
                    + " as it does not exist in " + getBlock().getBlockState());
        }

        return new IllegalArgumentException("Cannot set property " + property + " to " + newValue
                + " on block " + Block.REGISTRY.getNameForObject(getBlock())
                + ", it is not an allowed value");
    }
}
