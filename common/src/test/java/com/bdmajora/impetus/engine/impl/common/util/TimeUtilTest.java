package com.bdmajora.impetus.engine.impl.common.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {
    @Test
    void picksTheLargestUnitThatKeepsTheValueAboveOne() {
        assertEquals("1.500 ms", TimeUtil.stringifyTime(1_500_000, TimeUnit.NANOSECONDS));
        assertEquals("2.000 s", TimeUtil.stringifyTime(2000, TimeUnit.MILLISECONDS));
        assertEquals("500.0 ns", TimeUtil.stringifyTime(500, TimeUnit.NANOSECONDS));
        assertEquals("3.000 d", TimeUtil.stringifyTime(3, TimeUnit.DAYS));
    }

    @Test
    void fixedUnitsUseTheirAbbreviations() {
        assertEquals("1.000 us", TimeUtil.stringifyTime(1000, TimeUnit.NANOSECONDS, TimeUnit.MICROSECONDS));
        assertEquals("2.000 min", TimeUtil.stringifyTime(120, TimeUnit.SECONDS, TimeUnit.MINUTES));
        assertEquals("1.000 h", TimeUtil.stringifyTime(60, TimeUnit.MINUTES, TimeUnit.HOURS));
        new TimeUtil();
    }
}
