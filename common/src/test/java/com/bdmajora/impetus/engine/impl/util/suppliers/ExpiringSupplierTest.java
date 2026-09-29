package com.bdmajora.impetus.engine.impl.util.suppliers;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExpiringSupplierTest {
    @Test
    void cachesUntilExpiry() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        ExpiringSupplier<Integer> supplier = new ExpiringSupplier<>(calls::incrementAndGet, 1, TimeUnit.HOURS);
        assertEquals(1, supplier.get());
        assertEquals(1, supplier.get());
        ExpiringSupplier<Integer> quick = new ExpiringSupplier<>(calls::incrementAndGet, 1, TimeUnit.NANOSECONDS);
        assertEquals(2, quick.get());
        Thread.sleep(1);
        assertEquals(3, quick.get());
        assertThrows(NullPointerException.class, () -> new ExpiringSupplier<>(() -> null, 1, TimeUnit.HOURS).get());
    }
}
