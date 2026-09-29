package com.bdmajora.impetus.api;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionEventsTest {
    @Test
    void guiEventExposesMutablePages() {
        List<OptionPage> pages = new ArrayList<>();
        OptionGUIConstructionEvent event = new OptionGUIConstructionEvent(pages);
        OptionPage page = Mockito.mock(OptionPage.class);
        event.addPage(page);
        assertSame(pages, event.getPages());
        assertEquals(List.of(page), pages);
        assertFalse(event.isCancelable());
    }

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

    @Test
    void groupEventExposesIdAndOptions() {
        OptionIdentifier<Void> id = OptionIdentifier.create("t", "group");
        List<Option<?>> options = new ArrayList<>();
        OptionGroupConstructionEvent event = new OptionGroupConstructionEvent(id, options);
        assertSame(id, event.getId());
        assertSame(options, event.getOptions());
    }
}
