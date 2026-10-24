package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.gui.framework.TextFormattingStyle;
import com.bdmajora.impetus.engine.impl.gui.options.TextProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionImpactTest {
    private static final List<String> NAMES = List.of("LOW", "MEDIUM", "HIGH", "VARIES");
    private static final Map<OptionImpact, TextFormattingStyle> COLOURS = Map.of(
            OptionImpact.LOW, TextFormattingStyle.GREEN,
            OptionImpact.MEDIUM, TextFormattingStyle.YELLOW,
            OptionImpact.HIGH, TextFormattingStyle.GOLD,
            OptionImpact.VARIES, TextFormattingStyle.WHITE);

    // The label an impact should hand out: its own lang key, untranslated, in its colour
    private static TextComponent expected(OptionImpact impact) {
        return new TextComponent.Styled(
                new TextComponent.Translatable(List.of("impetus.option_impact." + impact.name().toLowerCase(Locale.ROOT)), List.of()),
                EnumSet.of(COLOURS.get(impact)));
    }

    @Test
    void theImpactsAreTheFourTheTooltipKnowsInRisingOrder() {
        OptionImpact[] impacts = OptionImpact.values();
        assertEquals(NAMES, Arrays.stream(impacts).map(Enum::name).toList());
        for (int i = 0; i < impacts.length; i++) {
            assertEquals(i, impacts[i].ordinal());
            assertEquals(NAMES.get(i), impacts[i].toString());
        }
        assertEquals(List.of(impacts), List.copyOf(EnumSet.allOf(OptionImpact.class)));
    }

    @Test
    void eachLabelIsItsOwnKeyInItsOwnColourLeftForTheDrawToTranslate() {
        for (OptionImpact impact : OptionImpact.values()) {
            TextComponent label = impact.getLocalizedName();
            assertEquals(expected(impact), label, impact.name());
            // One colour and nothing else, so the tooltip's grey never wins over it
            TextComponent.Styled styled = assertInstanceOf(TextComponent.Styled.class, label);
            assertEquals(1, styled.styles().size());
            assertTrue(styled.styles().iterator().next().isColor());
            // A key rather than resolved text, so a language switch reaches it
            TextComponent.Translatable key = assertInstanceOf(TextComponent.Translatable.class, styled.inner());
            assertTrue(key.args().isEmpty());
            assertEquals("impetus.option_impact." + impact.name().toLowerCase(Locale.ROOT) + "[styled]", label.toString());
        }
        // No two impacts share a colour
        assertEquals(OptionImpact.values().length, Set.copyOf(COLOURS.values()).size());
    }

    @Test
    void everyCallAnswersAnEqualLabelSoTextCachesKeepHitting() {
        for (OptionImpact impact : OptionImpact.values()) {
            TextComponent first = impact.getLocalizedName();
            TextComponent second = impact.getLocalizedName();
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            // Through the interface too, as the cycler and the tooltip reach it
            TextProvider provider = impact;
            assertEquals(first, provider.getLocalizedName());
        }
    }

    @Test
    void aChangeToAHandedOutLabelNeverReachesTheNextOne() {
        for (OptionImpact impact : OptionImpact.values()) {
            TextComponent.Styled handed = (TextComponent.Styled) impact.getLocalizedName();
            int hash = handed.hashCode();
            // Restyled, cleared and recoloured by whoever holds it
            handed.styles().add(TextFormattingStyle.STRIKETHROUGH);
            handed.styles().add(TextFormattingStyle.RED);
            assertEquals(expected(impact), impact.getLocalizedName(), impact.name());
            handed.styles().clear();
            assertEquals(expected(impact), impact.getLocalizedName(), impact.name());
            assertEquals(hash, impact.getLocalizedName().hashCode());
            // The key inside cannot be changed at all
            TextComponent.Translatable key = (TextComponent.Translatable) handed.inner();
            assertThrows(UnsupportedOperationException.class, () -> key.keys().set(0, "other"));
            @SuppressWarnings("unchecked")
            List<Object> args = (List<Object>) key.args();
            assertThrows(RuntimeException.class, () -> args.set(0, "other"));
        }
    }

    @Test
    void theEnumHoldsOnlyItsConstantsAndTheLabelParts() {
        assertTrue(Modifier.isPublic(OptionImpact.class.getModifiers()));
        assertTrue(Modifier.isFinal(OptionImpact.class.getModifiers()));
        assertTrue(TextProvider.class.isAssignableFrom(OptionImpact.class));
        for (OptionImpact impact : OptionImpact.values()) {
            assertSame(OptionImpact.class, impact.getClass());
        }
        Set<String> statics = Arrays.stream(OptionImpact.class.getDeclaredFields()).filter(f -> !f.isSynthetic() && Modifier.isStatic(f.getModifiers())).map(Field::getName).collect(Collectors.toSet());
        assertEquals(Set.copyOf(NAMES), statics);
        // Per constant: the shared key and the colour, both final
        Map<String, Class<?>> fields = Arrays.stream(OptionImpact.class.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).collect(Collectors.toMap(Field::getName, Field::getType));
        assertEquals(Map.of("key", TextComponent.class, "colour", TextFormattingStyle.class), fields);
        for (Field field : OptionImpact.class.getDeclaredFields()) {
            assertTrue(Modifier.isFinal(field.getModifiers()), field.getName());
        }
        Set<String> methods = Arrays.stream(OptionImpact.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).map(Method::getName).collect(Collectors.toSet());
        assertEquals(Set.of("values", "valueOf", "getLocalizedName"), methods);
    }

    @Test
    void valueOfAnswersOnlyExactNamesAndValuesIsFresh() {
        for (OptionImpact impact : OptionImpact.values()) {
            String name = impact.name();
            assertSame(impact, OptionImpact.valueOf(name));
            assertThrows(IllegalArgumentException.class, () -> OptionImpact.valueOf(name.toLowerCase(Locale.ROOT)));
            assertThrows(IllegalArgumentException.class, () -> OptionImpact.valueOf(" " + name));
        }
        // Sodium's old top impact is not one of ours
        assertThrows(IllegalArgumentException.class, () -> OptionImpact.valueOf("EXTREME"));
        assertThrows(IllegalArgumentException.class, () -> OptionImpact.valueOf(""));
        assertThrows(NullPointerException.class, () -> OptionImpact.valueOf(null));
        OptionImpact[] first = OptionImpact.values();
        Arrays.fill(first, null);
        assertEquals(NAMES, Arrays.stream(OptionImpact.values()).map(Enum::name).toList());
    }
}
