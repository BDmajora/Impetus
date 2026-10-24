package com.bdmajora.impetus.api.options.structure;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.control.Control;
import com.bdmajora.impetus.api.options.control.ControlElement;
import com.bdmajora.impetus.api.options.control.TickBoxControl;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OptionTest {
    private static final String MOD = "option_contract_test";
    private static final Dim2i DIM = new Dim2i(10, 20, 200, 18);

    // An option that answers nothing, so a default method asking more than it should fails the test
    private abstract static class Unasked<T> implements Option<T> {
        @Override
        public TextComponent getName() {
            throw new AssertionError("getName");
        }

        @Override
        public TextComponent getTooltip() {
            throw new AssertionError("getTooltip");
        }

        @Override
        public OptionImpact getImpact() {
            throw new AssertionError("getImpact");
        }

        @Override
        public Control<T> getControl() {
            throw new AssertionError("getControl");
        }

        @Override
        public T getValue() {
            throw new AssertionError("getValue");
        }

        @Override
        public void setValue(T value) {
            throw new AssertionError("setValue");
        }

        @Override
        public void reset() {
            throw new AssertionError("reset");
        }

        @Override
        public OptionStorage<?> getStorage() {
            throw new AssertionError("getStorage");
        }

        @Override
        public boolean isAvailable() {
            throw new AssertionError("isAvailable");
        }

        @Override
        public boolean hasChanged() {
            throw new AssertionError("hasChanged");
        }

        @Override
        public void applyChanges() {
            throw new AssertionError("applyChanges");
        }

        @Override
        public Collection<OptionFlag> getFlags() {
            throw new AssertionError("getFlags");
        }
    }

    // An option with a fixed id and the control the test gives it, counting how often the id is read
    private static final class Fixed<T> extends Unasked<T> {
        private final OptionIdentifier<T> id;
        Control<T> control;
        int idReads;

        Fixed(OptionIdentifier<T> id) {
            this.id = id;
        }

        @Override
        public OptionIdentifier<T> getId() {
            this.idReads++;
            return this.id;
        }

        @Override
        public Control<T> getControl() {
            return this.control;
        }
    }

    private static <T> Fixed<T> typed(String path, Class<T> type) {
        return new Fixed<>(OptionIdentifier.create(MOD, path, type));
    }

    // The value type the default answers for an id of the given type, asking the id once and nothing else
    private static Class<?> valueTypeOf(String path, Class<?> type) {
        Fixed<?> option = typed(path, type);
        Class<?> answered = option.getValueType();
        assertEquals(1, option.idReads, path);
        return answered;
    }

    @Test
    void theShapeIsUnchangedSoOptionsModsHaveCompiledStillLink() {
        Class<?> type = Option.class;
        assertTrue(type.isInterface() && Modifier.isPublic(type.getModifiers()));
        assertEquals(0, type.getInterfaces().length);
        assertEquals(1, type.getTypeParameters().length);
        assertEquals(0, type.getDeclaredFields().length);
        Map<String, Boolean> methods = Arrays.stream(type.getDeclaredMethods()).filter(m -> !m.isSynthetic())
                .collect(Collectors.toMap(m -> m.getReturnType().getSimpleName() + " " + m.getName() + Arrays.toString(Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).toArray()), Method::isDefault));
        assertEquals(Map.ofEntries(Map.entry("OptionIdentifier getId[]", true), Map.entry("Class getValueType[]", true),
                Map.entry("TextComponent getName[]", false), Map.entry("TextComponent getTooltip[]", false), Map.entry("OptionImpact getImpact[]", false),
                Map.entry("Control getControl[]", false), Map.entry("Object getValue[]", false), Map.entry("void setValue[Object]", false),
                Map.entry("void reset[]", false), Map.entry("OptionStorage getStorage[]", false), Map.entry("boolean isAvailable[]", false),
                Map.entry("boolean hasChanged[]", false), Map.entry("void applyChanges[]", false), Map.entry("Collection getFlags[]", false)), methods);
    }

    @Test
    void anOptionWithNoIdOfItsOwnHasNoneAndHoldsValuesOfAnyType() {
        Option<String> bare = new Unasked<>() {
        };
        assertNull(bare.getId());
        assertSame(Object.class, bare.getValueType());
        // The same through a mock that runs the real defaults
        @SuppressWarnings("unchecked")
        Option<Integer> mocked = Mockito.mock(Option.class, Mockito.CALLS_REAL_METHODS);
        assertNull(mocked.getId());
        assertSame(Object.class, mocked.getValueType());
    }

    @Test
    void missingAndUntypedIdsSayNothingAboutTheValue() {
        Fixed<Object> missing = new Fixed<>(null);
        assertSame(Object.class, missing.getValueType());
        assertEquals(1, missing.idReads);
        Fixed<Void> empty = new Fixed<>(OptionIdentifier.EMPTY);
        assertSame(Object.class, empty.getValueType());
        assertEquals(1, empty.idReads);
        // Untyped ids, as pages and groups carry, are typed void
        Fixed<Void> untyped = new Fixed<>(OptionIdentifier.create(MOD, "untyped"));
        assertSame(Object.class, untyped.getValueType());
        assertSame(Object.class, valueTypeOf("void_typed", Void.class));
        assertSame(Object.class, valueTypeOf("void_primitive_typed", void.class));
        // An id with no class at all cannot be made, so only a missing id reaches the null branch
        assertThrows(NullPointerException.class, () -> OptionIdentifier.create(MOD, "class_less", null));
    }

    @Test
    void aPrimitiveIdIsAnsweredByItsWrapperSoEveryValueIsAnInstanceOfIt() {
        Map<Class<?>, Class<?>> wrappers = Map.of(boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class, char.class, Character.class,
                int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class);
        wrappers.forEach((primitive, wrapper) -> assertSame(wrapper, valueTypeOf("primitive_" + primitive.getName(), primitive), primitive::getName));
        // A value read from the option is an instance of the answer and casts to it
        Fixed<Integer> number = typed("primitive_number", int.class);
        Integer value = 7;
        assertTrue(number.getValueType().isInstance(value));
        assertSame(value, number.getValueType().cast(value));
        // The same class an option built for the primitive answers, so both kinds of option meet a control the same way
        AtomicReference<Integer> data = new AtomicReference<>(0);
        OptionStorage<AtomicReference<Integer>> storage = () -> data;
        @SuppressWarnings("unchecked")
        OptionImpl<AtomicReference<Integer>, Integer> built = OptionImpl.createBuilder(int.class, storage)
                .setId(OptionIdentifier.create(MOD, "built_number", int.class))
                .setBinding(AtomicReference::set, AtomicReference::get)
                .setControl(o -> Mockito.mock(Control.class))
                .build();
        assertSame(built.getValueType(), typed("built_number", int.class).getValueType());
    }

    @Test
    void referenceAndArrayIdsAreAnsweredAsGiven() {
        // Arrays are reference types even of primitives, so int[] stays int[]
        for (Class<?> type : List.of(String.class, Integer.class, Boolean.class, OptionImpact.class, Runnable.class, List.class, Object.class, int[].class, Integer[][].class)) {
            assertSame(type, valueTypeOf("reference_" + type.getName(), type), type::getName);
        }
    }

    @Test
    void anIdThatCannotBeReadFailsTheValueTypeWithItsOwnFailure() {
        IllegalStateException failure = new IllegalStateException("no id");
        Option<Boolean> broken = new Unasked<>() {
            @Override
            public OptionIdentifier<Boolean> getId() {
                throw failure;
            }
        };
        // Left to the caller, which makes a placeholder of the row
        assertSame(failure, assertThrows(IllegalStateException.class, broken::getValueType));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aPrimitiveIdMeetsControlsByItsWrapper() {
        Fixed<Boolean> flag = typed("checked_boolean", boolean.class);
        flag.control = new TickBoxControl(flag);
        ControlElement<Boolean> element = Control.createElementFor(flag, DIM);
        assertSame(flag, element.getOption());
        // A mismatch names the option's side as the class its values have
        Fixed<Integer> number = typed("checked_int", int.class);
        number.control = (Control) new TickBoxControl((Option) number);
        IllegalStateException mismatch = assertThrows(IllegalStateException.class, () -> Control.createElementFor(number, DIM));
        assertEquals("Control of option " + MOD + ":checked_int edits java.lang.Boolean values but the option holds java.lang.Integer", mismatch.getMessage());
    }

    @Test
    void exactlyTheAnswersTheScreenToleratesAsNullAreMarkedNullable() throws IOException {
        // The marks are kept in the class file only, so they are read from it
        ClassModel model;
        try (InputStream in = Objects.requireNonNull(Option.class.getResourceAsStream("Option.class"))) {
            model = ClassFile.of().parse(in.readAllBytes());
        }
        Set<String> nullable = model.methods().stream()
                .filter(m -> m.findAttribute(Attributes.runtimeInvisibleAnnotations()).stream()
                        .flatMap(a -> a.annotations().stream())
                        .anyMatch(a -> a.className().stringValue().equals("Lorg/jetbrains/annotations/Nullable;")))
                .map(m -> m.methodName().stringValue())
                .collect(Collectors.toSet());
        assertEquals(Set.of("getId", "getTooltip", "getImpact", "getValue"), nullable);
    }
}
