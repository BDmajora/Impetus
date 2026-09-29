package com.bdmajora.impetus.engine.impl.compat.environment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {
    @Test
    void contextInfoNeverHoldsNulls() {
        GlContextInfo info = GlContextInfo.capture();
        assertEquals("", info.vendor());
        assertEquals("", info.renderer());
        assertEquals("", info.version());
        assertEquals(info, new GlContextInfo("", "", ""));
        assertNotNull(info.toString());
        assertEquals(info.hashCode(), new GlContextInfo("", "", "").hashCode());
    }

    @Test
    void osIsDetectedOnce() {
        assertSame(OsKind.current(), OsKind.current());
        assertEquals(OsKind.LINUX, OsKind.valueOf("LINUX"));
        assertEquals(4, OsKind.values().length);
    }
}
