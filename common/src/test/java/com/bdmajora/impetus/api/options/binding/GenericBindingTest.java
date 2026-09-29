package com.bdmajora.impetus.api.options.binding;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericBindingTest {
    @Test
    void delegatesToGetterAndSetter() {
        GenericBinding<AtomicInteger, Integer> binding = new GenericBinding<>(AtomicInteger::set, AtomicInteger::get);
        AtomicInteger storage = new AtomicInteger(1);
        assertEquals(1, binding.getValue(storage));
        binding.setValue(storage, 5);
        assertEquals(5, storage.get());
    }
}
