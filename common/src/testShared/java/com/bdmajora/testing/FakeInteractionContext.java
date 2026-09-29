package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.gui.framework.InteractionContext;

import java.util.EnumSet;

// Counts click sounds and lets a test hold modifier keys
public class FakeInteractionContext implements InteractionContext {
    public int clicks;
    public final EnumSet<SpecialKey> held = EnumSet.noneOf(SpecialKey.class);

    @Override
    public void playClickSound() {
        clicks++;
    }

    @Override
    public boolean isSpecialKeyDown(SpecialKey key) {
        return held.contains(key);
    }
}
