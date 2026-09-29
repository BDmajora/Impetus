package com.bdmajora.impetus.engine.impl.gui.console.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageLevelTest {
    @Test
    void hasThreeLevels() {
        assertEquals(3, MessageLevel.values().length);
        assertEquals(MessageLevel.WARN, MessageLevel.valueOf("WARN"));
    }
}
