package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionEventsTest {
    @Test
    void pageEventCollectsGroupsReadOnly() {
        OptionIdentifier<Void> id = OptionIdentifier.create("t", "page");
        TextComponent name = TextComponent.literal("Page");
        OptionPageConstructionEvent event = new OptionPageConstructionEvent(id, name);
        OptionGroup group = OptionGroup.createBuilder().build();
        event.addGroup(group);
        assertSame(id, event.getId());
        assertSame(name, event.getTranslationKey());
        assertEquals(List.of(group), event.getAdditionalGroups());
        assertThrows(UnsupportedOperationException.class, () -> event.getAdditionalGroups().add(group));
    }
}
