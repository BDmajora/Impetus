package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.testing.TestGl;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.List;

// Reads the calls recorded on the test GL service
public final class GlCalls {
    // Prevents instantiation
    private GlCalls() {
    }

    // Returns every recorded call of one method in call order
    public static List<Invocation> named(String name) {
        List<Invocation> calls = new ArrayList<>();
        for (Invocation invocation : Mockito.mockingDetails(TestGl.gl()).getInvocations()) {
            if (invocation.getMethod().getName().equals(name)) {
                calls.add(invocation);
            }
        }
        return calls;
    }

    // Returns the values set for one texture parameter through glTexParameteri in call order
    public static List<Integer> texParameter(int pname) {
        List<Integer> values = new ArrayList<>();
        for (Invocation invocation : named("glTexParameteri")) {
            if (invocation.<Integer>getArgument(1) == pname) {
                values.add(invocation.getArgument(2));
            }
        }
        return values;
    }

    // Returns the last value set for one texture parameter or null when none was set
    public static Integer lastTexParameter(int pname) {
        List<Integer> values = texParameter(pname);
        return values.isEmpty() ? null : values.get(values.size() - 1);
    }

    // Returns every texture name passed to either glDeleteTextures overload in call order
    public static List<Integer> deletedTextures() {
        List<Integer> names = new ArrayList<>();
        for (Invocation invocation : named("glDeleteTextures")) {
            Object argument = invocation.getArgument(0);
            if (argument instanceof int[] batch) {
                for (int name : batch) {
                    names.add(name);
                }
            } else {
                names.add((Integer) argument);
            }
        }
        return names;
    }

    // Returns how many times one method was called
    public static int count(String name) {
        return named(name).size();
    }
}
