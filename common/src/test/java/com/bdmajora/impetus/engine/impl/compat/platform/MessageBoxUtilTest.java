package com.bdmajora.impetus.engine.impl.compat.platform;

import org.junit.jupiter.api.Test;

class MessageBoxUtilTest {
    @Test
    void headlessRunsOnlyLog() {
        MessageBoxUtil.showWarning("title", "line one\nline two");
        MessageBoxUtil.showError("title", "boom");
    }
}
