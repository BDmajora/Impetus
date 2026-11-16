package com.bdmajora.coarctatio.state;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.dedup.DeduplicationCache;
import com.bdmajora.coarctatio.state.predicate.AllMatchAnyObject;
import com.bdmajora.coarctatio.state.predicate.AllMatchOne;
import com.bdmajora.coarctatio.state.predicate.AllMatchOneBoolean;
import com.bdmajora.coarctatio.state.predicate.CompositePredicate;
import com.bdmajora.coarctatio.state.predicate.NegatedPredicate;
import com.bdmajora.coarctatio.state.predicate.SingleMatchAny;
import com.bdmajora.coarctatio.state.predicate.SingleMatchOne;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.multipart.ICondition;

import java.util.ArrayList;
import java.util.List;

// Builds multipart predicates and shares equal ones.
public final class ConditionCanonicalizer {
    // Splitter of alternative values.
    private static final Splitter VALUE_SPLITTER = Splitter.on('|').omitEmptyStrings();

    // Pool of shared predicates.
    private static final DeduplicationCache<Predicate<IBlockState>> POOL =
            new DeduplicationCache<>("Multipart predicates", CoarctatioConfig.get().poolSizeLimit);

    // Prevents instantiation
    private ConditionCanonicalizer() {
    }

    // Opens the pool.
    public static void open() {
        POOL.open();
    }

    // Closes the pool.
    public static void close() {
        POOL.close();
    }

    // Returns the number of predicates answered by the pool.
    public static long sharedCount() {
        return POOL.shared();
    }

    // Returns a one line report of the pool.
    public static String statistics() {
        return POOL.toString();
    }

    // Returns the predicate for a property value and rejects an unknown property or value.
    public static Predicate<IBlockState> propertyValue(BlockStateContainer container, String key, String value) {
        IProperty<?> property = container.getProperty(key);

        if (property == null) {
            throw new RuntimeException("{key=" + key + ", value=" + value + "}: Definition: "
                    + container + " has no property: " + key);
        }

        String remaining = value;
        boolean negate = !remaining.isEmpty() && remaining.charAt(0) == '!';

        if (negate) {
            remaining = remaining.substring(1);
        }

        List<String> parts = VALUE_SPLITTER.splitToList(remaining);

        if (parts.isEmpty()) {
            throw new RuntimeException("{key=" + key + ", value=" + value + "}: has an empty value: " + value);
        }

        Predicate<IBlockState> predicate;

        if (parts.size() == 1) {
            predicate = intern(new SingleMatchOne(property, parseValue(container, property, parts.get(0), value)));
        } else {
            Object[] values = new Object[parts.size()];

            for (int i = 0; i < values.length; i++) {
                values[i] = parseValue(container, property, parts.get(i), value);
            }

            predicate = intern(new SingleMatchAny(property, values));
        }

        return negate ? intern(new NegatedPredicate(predicate)) : predicate;
    }

    // Returns a predicate that needs every predicate to match.
    public static Predicate<IBlockState> all(List<Predicate<IBlockState>> predicates) {
        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        Predicate<IBlockState> flattened = AllMatchOneBoolean.tryFlatten(predicates);

        if (flattened == null) {
            flattened = AllMatchOne.tryFlatten(predicates);
        }

        if (flattened == null) {
            flattened = AllMatchAnyObject.tryFlatten(predicates);
        }

        if (flattened == null) {
            flattened = CompositePredicate.all(predicates);
        }

        return intern(flattened);
    }

    // Returns a predicate that needs one predicate to match.
    public static Predicate<IBlockState> any(List<Predicate<IBlockState>> predicates) {
        if (predicates.size() == 1) {
            return predicates.get(0);
        }

        return intern(CompositePredicate.any(predicates));
    }

    // Returns the predicates of the conditions.
    public static List<Predicate<IBlockState>> resolve(Iterable<? extends ICondition> conditions,
                                                       BlockStateContainer container) {
        List<Predicate<IBlockState>> resolved = new ArrayList<>();

        for (ICondition condition : conditions) {
            resolved.add(condition.getPredicate(container));
        }

        return resolved;
    }

    // Returns the parsed property value and rejects an unknown one.
    private static Object parseValue(BlockStateContainer container, IProperty<?> property, String raw, String original) {
        Optional<?> parsed = property.parseValue(raw);

        if (!parsed.isPresent()) {
            throw new RuntimeException(container + ": has an unknown value: " + original);
        }

        return parsed.get();
    }

    // Returns the shared predicate equal to the predicate.
    private static Predicate<IBlockState> intern(Predicate<IBlockState> predicate) {
        return POOL.deduplicate(predicate);
    }
}
