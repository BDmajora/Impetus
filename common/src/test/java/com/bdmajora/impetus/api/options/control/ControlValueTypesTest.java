package com.bdmajora.impetus.api.options.control;

import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.engine.impl.util.Dim2i;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ControlValueTypesTest {
    private static final String GENERATED = "com/bdmajora/generated/";
    private static final String GONE = "L" + GENERATED + "Gone;";
    private static final String OBJECT = "Ljava/lang/Object;";

    private enum Mode { A, B }

    private enum Other { X }

    // Not final, and sealed by javac, since a constant with a body is a class of its own
    private enum Bodied {
        A {
        },
        B
    }

    private sealed interface Shape permits Circle, Square {
    }

    private record Circle() implements Shape {
    }

    private static final class Square implements Shape {
    }

    private sealed interface Loose permits Spread {
    }

    private static non-sealed class Spread implements Loose {
    }

    private abstract static sealed class Vehicle permits Runner, Plain {
    }

    private static final class Runner extends Vehicle implements Runnable {
        @Override
        public void run() {
        }
    }

    private static final class Plain extends Vehicle {
    }

    private sealed interface Tree permits Branch {
    }

    private sealed interface Branch extends Tree permits Twig {
    }

    private static final class Twig implements Branch, Runnable {
        @Override
        public void run() {
        }
    }

    private sealed interface Left permits Middle, LeftOnly {
    }

    private sealed interface Right permits Middle, RightOnly {
    }

    private static final class Middle implements Left, Right {
    }

    private static final class LeftOnly implements Left {
    }

    private static final class RightOnly implements Right {
    }

    // An open class and an interface it does not implement, met by this subclass
    private static final class RunnableList extends AbstractList<Object> implements Runnable {
        @Override
        public Object get(int index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void run() {
        }
    }

    // Minimal controls for the value type lookup, which reads only their classes; public where generated classes in another package extend them
    public abstract static class Stub<V> implements Control<V> {
        @Override
        public Option<V> getOption() {
            return null;
        }

        @Override
        public ControlElement<V> createElement(Dim2i dim) {
            return null;
        }

        @Override
        public int getMaxWidth() {
            return 0;
        }
    }

    private static final class LongStub extends Stub<Long> {
    }

    public static class PassOn<W> extends Stub<W> {
    }

    private static final class StringPassOn extends PassOn<String> {
    }

    private static class Relay<R> extends PassOn<R> {
    }

    private static final class RelayLeaf extends Relay<Short> {
    }

    public static class Swapped<A, B> extends Stub<B> {
    }

    private static final class SwappedLeaf extends Swapped<String, Integer> {
    }

    private static class Reordered<X, Y> extends Swapped<Y, X> {
    }

    private static final class ReorderedLeaf extends Reordered<Double, Short> {
    }

    private static final class ArrayStub extends Stub<String[]> {
    }

    public static class GenericArray<E> extends Stub<E[]> {
    }

    private static final class IntegerArrays extends GenericArray<Integer> {
    }

    private static final class IntArrays extends GenericArray<int[]> {
    }

    private static final class ListArrays extends GenericArray<List<String>> {
    }

    private static final class ListArrayArrays extends GenericArray<List<String>[]> {
    }

    private static class Grid<G> extends Stub<G[][]> {
    }

    private static final class LongGrid extends Grid<Long> {
    }

    private static final class ListStub extends Stub<List<String>> {
    }

    private static final class MapStub extends Stub<Map<String, List<Integer>>> {
    }

    public static class Bounded<N extends Number> extends Stub<N> {
    }

    private static class SelfBounded<C extends Comparable<C>> extends Stub<C> {
    }

    private static class Chained<A extends CharSequence, B extends A> extends Stub<B> {
    }

    private static class Intersected<T extends Number & Runnable> extends Stub<T> {
    }

    private static class InterfaceFirst<T extends Runnable & Serializable> extends Stub<T> {
    }

    @SuppressWarnings("rawtypes")
    private static final class RawLeaf extends PassOn {
    }

    @SuppressWarnings("rawtypes")
    private static final class RawBounded extends Bounded {
    }

    private static class Concrete<X> extends Stub<Long> {
    }

    @SuppressWarnings("rawtypes")
    private static final class RawConcrete extends Concrete {
    }

    @SuppressWarnings("rawtypes")
    private static final class RawControl implements Control {
        @Override
        public Option getOption() {
            return null;
        }

        @Override
        public ControlElement createElement(Dim2i dim) {
            return null;
        }

        @Override
        public int getMaxWidth() {
            return 0;
        }
    }

    public interface BooleanControl extends Control<Boolean> {
    }

    private static final class ViaInterface extends Stub<Boolean> implements BooleanControl {
    }

    public interface ValueControl<V> extends Control<V> {
    }

    private static final class ViaGenericInterface implements Comparable<ViaGenericInterface>, ValueControl<Double> {
        @Override
        public int compareTo(ViaGenericInterface other) {
            return 0;
        }

        @Override
        public Option<Double> getOption() {
            return null;
        }

        @Override
        public ControlElement<Double> createElement(Dim2i dim) {
            return null;
        }

        @Override
        public int getMaxWidth() {
            return 0;
        }
    }

    private interface DoubleValues extends Comparable<DoubleValues>, ValueControl<Double> {
    }

    private static final class TickSubclass extends TickBoxControl {
        TickSubclass(Option<Boolean> option) {
            super(option);
        }
    }

    // Inner classes that read their outer classes' parameters
    public static class Outer<T> {
        public class Inner extends Stub<T> {
        }

        public class Generic<U> extends Stub<U> {
        }

        public class Pair<U> extends Swapped<U, T> {
        }

        public class Relayed extends PassOn<T> {
        }

        public class Middle<M> {
            public class Deepest extends Swapped<M, T> {
            }

            public class Flipped extends Swapped<T, M> {
            }
        }

        public static class Nested<U> extends Stub<U> {
        }
    }

    private static final class InnerLeaf extends Outer<String>.Inner {
        InnerLeaf(Outer<String> outer) {
            outer.super();
        }
    }

    private static final class GenericLeaf extends Outer<String>.Generic<Integer> {
        GenericLeaf(Outer<String> outer) {
            outer.super();
        }
    }

    private static final class PairLeaf extends Outer<String>.Pair<Integer> {
        PairLeaf(Outer<String> outer) {
            outer.super();
        }
    }

    private static final class RelayedLeaf extends Outer<Byte>.Relayed {
        RelayedLeaf(Outer<Byte> outer) {
            outer.super();
        }
    }

    private static final class DeepestLeaf extends Outer<String>.Middle<Integer>.Deepest {
        DeepestLeaf(Outer<String>.Middle<Integer> middle) {
            middle.super();
        }
    }

    private static final class FlippedLeaf extends Outer<String>.Middle<Integer>.Flipped {
        FlippedLeaf(Outer<String>.Middle<Integer> middle) {
            middle.super();
        }
    }

    private static final class NestedLeaf extends Outer.Nested<Long> {
    }

    private static final class Factory<F> {
        Class<?> anonymous() {
            return new Stub<F>() {
            }.getClass();
        }
    }

    private static <N extends Number> Class<?> localOver() {
        class Local extends Stub<N> {
        }
        return Local.class;
    }

    // Defines classes from bytes the test writes, so their signatures can say what javac never would, and records each class it lacks
    private static final class Generated extends ClassLoader {
        private final Map<String, byte[]> classes = new HashMap<>();
        // Class files handed out as resources, the defined bytes unless a test swaps them
        private final Map<String, Supplier<InputStream>> files = new HashMap<>();
        private final List<String> missed = new ArrayList<>();

        Generated() {
            super(ControlValueTypesTest.class.getClassLoader());
        }

        // Adds the abstract class com.bdmajora.generated.<name> with the given signature over the erased supertypes the JVM checks
        Generated add(String name, String signature, String superclass, String... interfaces) {
            return this.add(name, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, signature, superclass, interfaces, writer -> { });
        }

        // Adds a class whose writer the test also gives attributes or members
        Generated add(String name, int access, String signature, String superclass, String[] interfaces, Consumer<ClassWriter> extra) {
            byte[] bytes = written(name, access, signature, superclass, interfaces, extra);
            this.classes.put((GENERATED + name).replace('/', '.'), bytes);
            this.files.put(GENERATED + name + ".class", () -> new ByteArrayInputStream(bytes));
            return this;
        }

        // Hands out the given stream as the class file of a generated class, or no class file when it gives null
        Generated file(String name, Supplier<InputStream> file) {
            this.files.put(GENERATED + name + ".class", file);
            return this;
        }

        Class<?> get(String name) {
            try {
                return Class.forName((GENERATED + name).replace('/', '.'), false, this);
            } catch (ClassNotFoundException missing) {
                throw new AssertionError(missing);
            }
        }

        List<String> missed() {
            return List.copyOf(this.missed);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            Supplier<InputStream> file = this.files.get(name);
            return file != null ? file.get() : super.getResourceAsStream(name);
        }

        // Runs under the loader's own lock, which loadClass takes for a loader that is not parallel capable
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = this.classes.get(name);
            if (bytes == null) {
                this.missed.add(name);
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static byte[] written(String name, int access, String signature, String superclass, String[] interfaces, Consumer<ClassWriter> extra) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, access, GENERATED + name, signature, superclass, interfaces);
        extra.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String internal(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static String signature(Class<?> type, String... arguments) {
        return "L" + internal(type) + (arguments.length == 0 ? "" : "<" + String.join("", arguments) + ">") + ";";
    }

    private static Class<?> declared(Class<?> type) {
        return ControlValueTypes.declared(type);
    }

    @Test
    void aConcreteArgumentIsTheValueTypeHoweverFarDownTheClassSits() {
        assertSame(Long.class, new LongStub().getValueType());
        assertSame(String.class, new StringPassOn().getValueType());
        assertSame(Short.class, declared(RelayLeaf.class));
        // The right one of several parameters, even when a parent passes them on in another order
        assertSame(Integer.class, declared(SwappedLeaf.class));
        assertSame(Double.class, declared(ReorderedLeaf.class));
        assertSame(Boolean.class, declared(TickSubclass.class));
        assertSame(Character.class, new Stub<Character>() {
        }.getValueType());
    }

    @Test
    void interfacesAreFollowedAndUnrelatedOnesAheadOfThemPassedOver() {
        assertSame(Boolean.class, declared(ViaInterface.class));
        assertSame(Double.class, new ViaGenericInterface().getValueType());
        // An interface is a control class of its own, open or bound
        assertSame(Boolean.class, declared(BooleanControl.class));
        assertSame(Object.class, declared(ValueControl.class));
        assertSame(Double.class, declared(DoubleValues.class));
    }

    @Test
    void genericArgumentsEraseToTheirClassAndArraysKeepTheirDimensions() {
        assertSame(List.class, declared(ListStub.class));
        assertSame(Map.class, declared(MapStub.class));
        assertSame(String[].class, declared(ArrayStub.class));
        assertSame(Integer[].class, declared(IntegerArrays.class));
        assertSame(int[][].class, declared(IntArrays.class));
        assertSame(List[].class, declared(ListArrays.class));
        assertSame(List[][].class, declared(ListArrayArrays.class));
        assertSame(Long[][].class, declared(LongGrid.class));
        assertSame(Object[][].class, declared(Grid.class));
    }

    @Test
    void aTypeLeftOpenStandsForItsFirstBoundAndARawOneForAnything() {
        assertSame(Object.class, new PassOn<String>().getValueType());
        assertSame(Number.class, new Bounded<Integer>().getValueType());
        assertSame(Comparable.class, declared(SelfBounded.class));
        assertSame(CharSequence.class, declared(Chained.class));
        assertSame(Number.class, declared(Intersected.class));
        assertSame(Runnable.class, declared(InterfaceFirst.class));
        assertSame(Object[].class, declared(GenericArray.class));
        assertSame(Object.class, declared(RawLeaf.class));
        assertSame(Object.class, declared(RawControl.class));
        // A raw parent still has its own bounds and its own concrete arguments
        assertSame(Number.class, declared(RawBounded.class));
        assertSame(Long.class, declared(RawConcrete.class));
        // Parameters of methods and of classes around an anonymous class are never bound
        assertSame(Number.class, declared(localOver()));
        assertSame(Object.class, declared(new Factory<String>().anonymous()));
    }

    @Test
    void innerClassesTakeTheArgumentsTheirOuterClassesAreGiven() {
        assertSame(Object.class, declared(Outer.Inner.class));
        assertSame(String.class, declared(InnerLeaf.class));
        assertSame(Integer.class, declared(GenericLeaf.class));
        assertSame(String.class, declared(PairLeaf.class));
        assertSame(Byte.class, declared(RelayedLeaf.class));
        // Through every enclosing level, to whichever level's parameter the control names
        assertSame(String.class, declared(DeepestLeaf.class));
        assertSame(Integer.class, declared(FlippedLeaf.class));
        assertSame(Object.class, declared(Outer.Middle.Deepest.class));
        // A static nested class has no outer arguments, and its raw outer class gives none
        assertSame(Long.class, declared(NestedLeaf.class));
        assertSame(Object.class, declared(Outer.Nested.class));
    }

    @Test
    void controlItselfAndClassesOutsideItAreAnythingWithoutTheirSignaturesRead() {
        assertSame(Object.class, declared(Control.class));
        assertSame(Object.class, declared(String.class));
        assertSame(Object.class, declared(Object.class));
        assertSame(Object.class, declared(int.class));
        assertSame(Object.class, declared(void.class));
        assertSame(Object.class, declared(Control[].class));
        assertSame(Object.class, declared(LongStub[].class));
        // A proxy implements Control raw
        Object proxy = Proxy.newProxyInstance(Control.class.getClassLoader(), new Class<?>[]{Control.class}, (self, method, args) -> null);
        assertSame(Object.class, declared(proxy.getClass()));
        // A class outside Control is answered before its signature, which here names a missing class, is read
        Generated generated = new Generated().add("Outside", OBJECT + signature(Supplier.class, GONE), "java/lang/Object", internal(Supplier.class));
        assertSame(Object.class, declared(generated.get("Outside")));
        assertEquals(List.of(), generated.missed());
        assertThrows(NullPointerException.class, () -> declared(null));
    }

    @Test
    void eachClassIsResolvedOnceAndAnsweredTheSameAfterwards() {
        ClassValue<?> cache = Statics.get(ControlValueTypes.class, "DECLARED");
        assertSame(Long.class, cache.get(LongStub.class));
        for (int i = 0; i < 1000; i++) {
            assertSame(Long.class, new LongStub().getValueType());
            assertSame(Boolean.class, declared(TickBoxControl.class));
        }
    }

    @Test
    void aSignatureNamingAMissingClassIsRebuiltFromTheClassFileOnce() {
        Generated generated = new Generated()
                .add("Direct", OBJECT + signature(Control.class, GONE), "java/lang/Object", internal(Control.class))
                .add("ThroughStub", signature(Stub.class, GONE), internal(Stub.class))
                .add("ThroughBounded", signature(Bounded.class, GONE), internal(Bounded.class))
                // The superclass is read apart from the interfaces, so a missing class among them leaves it whole
                .add("Superclass", signature(Stub.class, "Ljava/lang/Long;") + signature(Supplier.class, GONE), internal(Stub.class), internal(Supplier.class))
                .add("Erased", OBJECT + signature(Supplier.class, GONE) + signature(BooleanControl.class), "java/lang/Object", internal(Supplier.class), internal(BooleanControl.class))
                .add("ErasedOpen", OBJECT + signature(Supplier.class, GONE) + signature(ValueControl.class, "Ljava/lang/Double;"), "java/lang/Object",
                        internal(Supplier.class), internal(ValueControl.class));
        assertSame(Object.class, declared(generated.get("Direct")));
        assertSame(Object.class, declared(generated.get("ThroughStub")));
        assertSame(Number.class, declared(generated.get("ThroughBounded")));
        assertSame(Long.class, declared(generated.get("Superclass")));
        assertSame(Boolean.class, declared(generated.get("Erased")));
        // Rebuilt from the class file one interface at a time, the missing class costs Supplier's argument and leaves ValueControl's
        assertSame(Double.class, declared(generated.get("ErasedOpen")));
        List<String> missed = generated.missed();
        assertFalse(missed.isEmpty());
        assertTrue(missed.stream().allMatch((GENERATED + "Gone").replace('/', '.')::equals), missed::toString);
        // A failure is answered like any other class, so the signature is never read again
        for (String name : List.of("Direct", "ThroughStub", "ThroughBounded", "Superclass", "Erased", "ErasedOpen")) {
            declared(generated.get(name));
        }
        assertEquals(missed, generated.missed());
    }

    @Test
    void aClassThatWillNotLinkCountsAsMissing() {
        // Broken is there to be found but its own superclass is not, so loading it throws NoClassDefFoundError
        String broken = "L" + GENERATED + "Broken;";
        Generated generated = new Generated()
                .add("Broken", null, GENERATED + "Gone")
                .add("Direct", OBJECT + signature(Control.class, broken), "java/lang/Object", internal(Control.class))
                .add("Superclass", signature(Stub.class, "Ljava/lang/Long;") + signature(Supplier.class, broken), internal(Stub.class), internal(Supplier.class));
        assertThrows(NoClassDefFoundError.class, () -> generated.get("Direct").getGenericInterfaces());
        assertSame(Object.class, declared(generated.get("Direct")));
        assertSame(Long.class, declared(generated.get("Superclass")));
    }

    @Test
    void theWrongNumberOfArgumentsFallsBackToTheErasedSupertypes() {
        // As a class compiled against an older shape of a generic parent carries
        Generated generated = new Generated()
                .add("TwoForOne", OBJECT + signature(Control.class, "Ljava/lang/String;", "Ljava/lang/Integer;"), "java/lang/Object", internal(Control.class))
                .add("PassedTwo", signature(PassOn.class, "Ljava/lang/String;", "Ljava/lang/Integer;"), internal(PassOn.class))
                .add("BoundedTwo", signature(Bounded.class, "Ljava/lang/Integer;", "Ljava/lang/Integer;"), internal(Bounded.class))
                .add("OneForTwo", signature(Swapped.class, "Ljava/lang/String;"), internal(Swapped.class));
        assertSame(Object.class, declared(generated.get("TwoForOne")));
        assertSame(Object.class, declared(generated.get("PassedTwo")));
        assertSame(Number.class, declared(generated.get("BoundedTwo")));
        assertSame(Object.class, declared(generated.get("OneForTwo")));
    }

    @Test
    void aMalformedSignatureFallsBackToTheErasedSupertypes() {
        Generated generated = new Generated()
                .add("ViaInterface", "Ljava/lang/Object;<<<;", "java/lang/Object", internal(BooleanControl.class))
                .add("ViaSuperclass", "garbage", internal(TickBoxControl.class))
                .add("Raw", "L;", "java/lang/Object", internal(Control.class))
                // A primitive cannot be a type argument
                .add("Primitive", OBJECT + "L" + internal(Control.class) + "<I>;", internal(Stub.class), internal(BooleanControl.class));
        assertSame(Boolean.class, declared(generated.get("ViaInterface")));
        assertSame(Boolean.class, declared(generated.get("ViaSuperclass")));
        assertSame(Object.class, declared(generated.get("Raw")));
        assertSame(Boolean.class, declared(generated.get("Primitive")));
    }

    @Test
    void aParameterWhoseBoundWillNotLoadLeavesTheParentsOwnAnswer() {
        Generated generated = new Generated()
                .add("Direct", "<A:" + GONE + ">" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("ThroughStub", "<A:" + GONE + ">" + signature(Stub.class, "TA;"), internal(Stub.class))
                .add("ThroughBounded", "<A:" + GONE + ">" + signature(Bounded.class, "TA;"), internal(Bounded.class))
                // A parameter no declaration has
                .add("Undeclared", OBJECT + signature(Control.class, "TZ;"), "java/lang/Object", internal(Control.class));
        assertSame(Object.class, declared(generated.get("Direct")));
        assertSame(Object.class, declared(generated.get("ThroughStub")));
        assertSame(Number.class, declared(generated.get("ThroughBounded")));
        assertSame(Object.class, declared(generated.get("Undeclared")));
    }

    @Test
    void aRawParentIsResolvedOnceForAllOfItsSubclasses() {
        // The parent's bound is missing, so each time its own answer is worked out the loader is asked for that class again
        Generated generated = new Generated()
                .add("Parent", "<A:" + GONE + ">" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("First", null, GENERATED + "Parent")
                .add("Second", null, GENERATED + "Parent");
        assertSame(Object.class, declared(generated.get("First")));
        int asked = generated.missed().size();
        assertTrue(asked > 0);
        assertSame(Object.class, declared(generated.get("Second")));
        assertSame(Object.class, declared(generated.get("Parent")));
        assertEquals(asked, generated.missed().size());
    }

    @Test
    void cyclesOfBoundsEndInsteadOfOverflowingTheStack() {
        Generated generated = new Generated()
                .add("Pair", "<A:TB;B:TA;>" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("Self", "<A:TA;>" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("Three", "<A:TB;B:TC;C:TA;>" + signature(PassOn.class, "TB;"), internal(PassOn.class))
                .add("Array", "<A:[TA;>" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("Arrays", "<A:[TB;B:[[TA;>" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("ArrayPassed", "<A:[TA;>" + signature(PassOn.class, "[TA;"), internal(PassOn.class))
                // A chain that does not close on itself still reaches its bound
                .add("Chain", "<A:Ljava/lang/Number;B:TA;C:TB;>" + OBJECT + signature(Control.class, "TC;"), "java/lang/Object", internal(Control.class));
        assertSame(Object.class, declared(generated.get("Pair")));
        assertSame(Object.class, declared(generated.get("Self")));
        assertSame(Object.class, declared(generated.get("Three")));
        // Unknown inside the array the bound wraps it in leaves the whole array unknown
        assertSame(Object.class, declared(generated.get("Array")));
        assertSame(Object.class, declared(generated.get("Arrays")));
        assertSame(Object.class, declared(generated.get("ArrayPassed")));
        assertSame(Number.class, declared(generated.get("Chain")));
    }

    @Test
    void wildcardsStandForTheirUpperBound() {
        Generated generated = new Generated()
                .add("Extends", OBJECT + signature(Control.class, "+Ljava/lang/Number;"), "java/lang/Object", internal(Control.class))
                .add("Super", OBJECT + signature(Control.class, "-Ljava/lang/Number;"), "java/lang/Object", internal(Control.class))
                .add("Any", OBJECT + signature(Control.class, "*"), "java/lang/Object", internal(Control.class))
                .add("Passed", signature(PassOn.class, "+Ljava/lang/String;"), internal(PassOn.class))
                .add("Variable", "<A:Ljava/lang/Long;>" + OBJECT + signature(Control.class, "+TA;"), "java/lang/Object", internal(Control.class))
                .add("Owner", "L" + internal(Outer.class) + "<+Ljava/lang/Integer;>.Inner;", internal(Outer.Inner.class))
                // A parameter given nothing narrower than Object keeps its own bound
                .add("BoundedAny", signature(Bounded.class, "*"), internal(Bounded.class))
                .add("BoundedSuper", signature(Bounded.class, "-Ljava/lang/Integer;"), internal(Bounded.class))
                .add("BoundedObject", signature(Bounded.class, OBJECT), internal(Bounded.class));
        assertSame(Number.class, declared(generated.get("Extends")));
        assertSame(Object.class, declared(generated.get("Super")));
        assertSame(Object.class, declared(generated.get("Any")));
        assertSame(String.class, declared(generated.get("Passed")));
        assertSame(Long.class, declared(generated.get("Variable")));
        assertSame(Integer.class, declared(generated.get("Owner")));
        assertSame(Number.class, declared(generated.get("BoundedAny")));
        assertSame(Number.class, declared(generated.get("BoundedSuper")));
        assertSame(Number.class, declared(generated.get("BoundedObject")));
    }

    @Test
    void aTypeOfNoKindReflectionHandsOutIsUnknown() {
        // Reflection gives only classes, parameterized and array types, parameters and wildcards, so these reach the last guard
        Type foreign = new Type() {
        };
        assertNull(Statics.call(ControlValueTypes.class, "erase", foreign, Map.of()));
        assertNull(Statics.call(ControlValueTypes.class, "erase", null, Map.of()));
    }

    @Test
    void aStaleSignatureThatMissesControlFallsBackToTheErasedSupertypes() {
        // A transformer that adds an interface or swaps the superclass without touching the signature leaves one like these
        Generated generated = new Generated()
                .add("AddedInterface", OBJECT + signature(Supplier.class, "Ljava/lang/String;"), "java/lang/Object", internal(Supplier.class), internal(BooleanControl.class))
                .add("NoInterfaces", OBJECT, "java/lang/Object", internal(Runnable.class), internal(Control.class))
                .add("ClassAsInterface", OBJECT + "Ljava/lang/String;", "java/lang/Object", internal(BooleanControl.class))
                .add("SwappedSuperclass", OBJECT, internal(TickBoxControl.class));
        assertSame(Boolean.class, declared(generated.get("AddedInterface")));
        assertSame(Object.class, declared(generated.get("NoInterfaces")));
        assertSame(Boolean.class, declared(generated.get("ClassAsInterface")));
        assertSame(Boolean.class, declared(generated.get("SwappedSuperclass")));
    }

    @Test
    void anUnreadableArgumentCostsOnlyItselfOnceTheSupertypesAreRebuiltFromTheClassFile() {
        String missing = signature(Supplier.class, GONE);
        String[] both = {internal(Supplier.class), internal(ValueControl.class)};
        Generated generated = new Generated()
                .add("Own", "<A:Ljava/lang/Long;>" + OBJECT + missing + signature(ValueControl.class, "TA;"), "java/lang/Object", both)
                .add("WrongArity", OBJECT + signature(Supplier.class, "Ljava/lang/String;", "Ljava/lang/Integer;") + signature(ValueControl.class, "Ljava/lang/Double;"),
                        "java/lang/Object", both)
                .add("Direct", OBJECT + missing + signature(Control.class, "Ljava/lang/Short;"), "java/lang/Object", internal(Supplier.class), internal(Control.class))
                .add("DirectWrongArity", OBJECT + missing + signature(Control.class, "Ljava/lang/Short;", "Ljava/lang/Short;"), "java/lang/Object",
                        internal(Supplier.class), internal(Control.class))
                // A superclass is rebuilt argument by argument, with each class around it
                .add("Known", signature(Swapped.class, GONE, "Ljava/lang/Integer;"), internal(Swapped.class))
                .add("Unknown", signature(Swapped.class, "Ljava/lang/Integer;", GONE), internal(Swapped.class))
                .add("Inner", "L" + internal(Outer.class) + "<Ljava/lang/Integer;>.Pair<" + GONE + ">;", internal(Outer.Pair.class))
                .add("Arrays", OBJECT + missing + signature(ValueControl.class, "[[I"), "java/lang/Object", both)
                .add("VariableArray", "<A:Ljava/lang/Long;>" + OBJECT + missing + signature(ValueControl.class, "[TA;"), "java/lang/Object", both)
                .add("MissingArray", OBJECT + missing + signature(ValueControl.class, "[" + GONE), "java/lang/Object", both)
                .add("TooDeep", "<A:" + "[".repeat(255) + "I>" + OBJECT + missing + signature(ValueControl.class, "[TA;"), "java/lang/Object", both)
                .add("Nested", OBJECT + missing + signature(ValueControl.class, signature(List.class, GONE)), "java/lang/Object", both)
                .add("Extends", OBJECT + missing + signature(ValueControl.class, "+Ljava/lang/Number;"), "java/lang/Object", both)
                .add("Super", OBJECT + missing + signature(ValueControl.class, "-Ljava/lang/Number;"), "java/lang/Object", both)
                .add("Any", OBJECT + missing + signature(ValueControl.class, "*"), "java/lang/Object", both)
                .add("Cycle", "<A:TB;B:TA;>" + OBJECT + missing + signature(ValueControl.class, "TA;"), "java/lang/Object", both)
                .add("Undeclared", OBJECT + missing + signature(ValueControl.class, "TZ;"), "java/lang/Object", both);
        assertSame(Long.class, declared(generated.get("Own")));
        assertSame(Double.class, declared(generated.get("WrongArity")));
        assertSame(Short.class, declared(generated.get("Direct")));
        assertSame(Object.class, declared(generated.get("DirectWrongArity")));
        assertSame(Integer.class, declared(generated.get("Known")));
        assertSame(Object.class, declared(generated.get("Unknown")));
        assertSame(Integer.class, declared(generated.get("Inner")));
        assertSame(int[][].class, declared(generated.get("Arrays")));
        assertSame(Long[].class, declared(generated.get("VariableArray")));
        assertSame(Object.class, declared(generated.get("MissingArray")));
        assertSame(Object.class, declared(generated.get("TooDeep")));
        // Only the outermost class of an argument counts, so a missing one inside it costs nothing
        assertSame(List.class, declared(generated.get("Nested")));
        assertSame(Number.class, declared(generated.get("Extends")));
        assertSame(Object.class, declared(generated.get("Super")));
        assertSame(Object.class, declared(generated.get("Any")));
        assertSame(Object.class, declared(generated.get("Cycle")));
        assertSame(Object.class, declared(generated.get("Undeclared")));
    }

    @Test
    void withoutAReadableClassFileOfItsOwnAClassFallsBackToTheErasedSupertypes() {
        String open = OBJECT + signature(Supplier.class, GONE) + signature(ValueControl.class, "Ljava/lang/Double;");
        String[] both = {internal(Supplier.class), internal(ValueControl.class)};
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        byte[] truncated = written("Truncated", access, open, "java/lang/Object", both, writer -> { });
        Generated generated = new Generated()
                .add("Served", open, "java/lang/Object", both)
                .add("Hidden", open, "java/lang/Object", both).file("Hidden", () -> null)
                .add("Unreadable", open, "java/lang/Object", both).file("Unreadable", () -> new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("unreadable");
                    }
                })
                .add("Garbage", open, "java/lang/Object", both).file("Garbage", () -> new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .add("Truncated", open, "java/lang/Object", both).file("Truncated", () -> new ByteArrayInputStream(Arrays.copyOf(truncated, truncated.length / 2)))
                // A transforming loader may hand out another class's bytes or the bytes before a signature was added
                .add("OtherClass", open, "java/lang/Object", both)
                .file("OtherClass", () -> new ByteArrayInputStream(written("Elsewhere", access, open, "java/lang/Object", both, writer -> { })))
                .add("Unsigned", open, "java/lang/Object", both)
                .file("Unsigned", () -> new ByteArrayInputStream(written("Unsigned", access, null, "java/lang/Object", both, writer -> { })));
        assertSame(Double.class, declared(generated.get("Served")));
        for (String name : List.of("Hidden", "Unreadable", "Garbage", "Truncated", "OtherClass", "Unsigned")) {
            assertSame(Object.class, declared(generated.get(name)), name);
        }
    }

    @Test
    void parametersOfTheScopesAroundAClassAreFoundForItsRebuiltSupertypes() {
        String host = GENERATED + "Host";
        String shell = GENERATED + "Shell";
        String[] both = {internal(Supplier.class), internal(ValueControl.class)};
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        Generated generated = new Generated()
                .add("Host", access, "<T:Ljava/lang/Short;>" + OBJECT, "java/lang/Object", new String[0], writer -> {
                    writer.visitInnerClass(host + "$Member", host, "Member", Opcodes.ACC_PUBLIC);
                    writer.visitMethod(access, "make", "()V", "<M:Ljava/lang/Integer;>()V", null).visitEnd();
                    MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", "<C:Ljava/lang/Byte;>()V", null);
                    constructor.visitCode();
                    constructor.visitVarInsn(Opcodes.ALOAD, 0);
                    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    constructor.visitInsn(Opcodes.RETURN);
                    constructor.visitMaxs(1, 1);
                    constructor.visitEnd();
                })
                .add("Host$Member", access, naming("T"), "java/lang/Object", both, writer -> writer.visitInnerClass(host + "$Member", host, "Member", Opcodes.ACC_PUBLIC))
                .add("Host$1Method", access, naming("M"), "java/lang/Object", both, local("Host$1Method", host, "make", "()V"))
                .add("Host$2Outward", access, naming("T"), "java/lang/Object", both, local("Host$2Outward", host, "make", "()V"))
                .add("Host$3Constructor", access, naming("C"), "java/lang/Object", both, local("Host$3Constructor", host, "<init>", "()V"))
                .add("Host$4Absent", access, naming("T"), "java/lang/Object", both, local("Host$4Absent", host, "absent", "()V"))
                .add("Ping", access, naming("Z"), "java/lang/Object", both, local("Ping", GENERATED + "Pong", null, null))
                .add("Pong", access, OBJECT, "java/lang/Object", new String[0], local("Pong", GENERATED + "Ping", null, null))
                .add("Selfish", access, naming("T"), "java/lang/Object", both, local("Selfish", GENERATED + "Selfish", null, null))
                .add("Lost", access, naming("T"), "java/lang/Object", both, local("Lost", GENERATED + "Gone", null, null))
                .add("Shell", access, "garbage", "java/lang/Object", new String[0], writer -> writer.visitInnerClass(shell + "$Member", shell, "Member", Opcodes.ACC_PUBLIC))
                .add("Shell$Member", access, naming("T"), "java/lang/Object", both, writer -> writer.visitInnerClass(shell + "$Member", shell, "Member", Opcodes.ACC_PUBLIC));
        assertSame(Short.class, declared(generated.get("Host$Member")));
        assertSame(Integer.class, declared(generated.get("Host$1Method")));
        // Past the method to the class that declares it
        assertSame(Short.class, declared(generated.get("Host$2Outward")));
        assertSame(Byte.class, declared(generated.get("Host$3Constructor")));
        // Reflection throws InternalError for an enclosing method its class lacks, yet the class around it is still searched
        assertThrows(InternalError.class, () -> generated.get("Host$4Absent").getEnclosingMethod());
        assertSame(Short.class, declared(generated.get("Host$4Absent")));
        // Two classes that each name the other as the class around them are searched once each
        assertSame(Object.class, declared(generated.get("Ping")));
        // A class named as around itself, or one that is missing, leaves no scope past the class's own
        assertThrows(InternalError.class, () -> generated.get("Selfish").getEnclosingClass());
        assertSame(Object.class, declared(generated.get("Selfish")));
        assertThrows(NoClassDefFoundError.class, () -> generated.get("Lost").getEnclosingClass());
        assertSame(Object.class, declared(generated.get("Lost")));
        // A class around it whose own signature is malformed declares nothing to find
        assertSame(Object.class, declared(generated.get("Shell$Member")));
    }

    @Test
    void aRebuiltSupertypeTakesOnlyWhatItsClassReallyDeclares() {
        Generated generated = new Generated()
                // A top-level class whose name holds a dollar sign has no class around it, whatever a signature calls it
                .add("Out$In", signature(Stub.class, "Ljava/lang/Long;"), internal(Stub.class))
                .add("Claims", "L" + GENERATED + "Out<" + GONE + ">.In;", GENERATED + "Out$In")
                // An interface whose own signature is malformed has no parameters to bind, so its erased supertypes answer for it
                .add("Shapeless", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, "garbage", "java/lang/Object", new String[]{internal(BooleanControl.class)},
                        writer -> { })
                .add("UsesShapeless", OBJECT + signature(Supplier.class, GONE) + "L" + GENERATED + "Shapeless<Ljava/lang/Long;>;", "java/lang/Object",
                        internal(Supplier.class), GENERATED + "Shapeless")
                // A signature listing another interface than the class has leaves the class's own one erased
                .add("Mislisted", OBJECT + signature(Supplier.class, GONE) + signature(ValueControl.class, "Ljava/lang/Double;"), "java/lang/Object",
                        internal(Supplier.class), internal(BooleanControl.class))
                // A class claiming to be a member of one that does not list it makes reflection throw, which costs only the class around it
                .add("Stranger", OBJECT, "java/lang/Object")
                .add("Disowned", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "<A:Ljava/lang/Object;B:Ljava/lang/Object;>" + signature(Swapped.class, "TA;", "TB;"),
                        internal(Swapped.class), new String[0], writer -> writer.visitInnerClass(GENERATED + "Disowned", GENERATED + "Stranger", "Disowned", Opcodes.ACC_PUBLIC))
                .add("Heir", "L" + GENERATED + "Disowned<" + GONE + "Ljava/lang/Integer;>;", GENERATED + "Disowned");
        assertSame(Long.class, declared(generated.get("Claims")));
        assertSame(Boolean.class, declared(generated.get("UsesShapeless")));
        assertSame(Boolean.class, declared(generated.get("Shapeless")));
        assertSame(Boolean.class, declared(generated.get("Mislisted")));
        assertThrows(IncompatibleClassChangeError.class, () -> generated.get("Disowned").getDeclaringClass());
        assertSame(Integer.class, declared(generated.get("Heir")));
    }

    // The attributes javac gives a local class, which the JVM reads its enclosing method attribute only alongside
    private static Consumer<ClassWriter> local(String name, String around, String method, String descriptor) {
        return writer -> {
            writer.visitOuterClass(around, method, descriptor);
            writer.visitInnerClass(GENERATED + name, null, name.substring(name.indexOf('$') + 1), 0);
        };
    }

    // A class signature whose interfaces reflection cannot build, the second naming the given parameter for ValueControl
    private static String naming(String parameter) {
        return OBJECT + signature(Supplier.class, GONE) + signature(ValueControl.class, "T" + parameter + ";");
    }

    @Test
    void anArrayOfTheDeepestKindIsNotNestedFurther() {
        // 254 dimensions bound to E still nest once more into the JVM's limit of 255, which cannot be passed
        Generated generated = new Generated()
                .add("Deep", signature(GenericArray.class, "[".repeat(254) + "I"), internal(GenericArray.class))
                .add("Deepest", signature(GenericArray.class, "[".repeat(255) + "I"), internal(GenericArray.class));
        Class<?> deep = declared(generated.get("Deep"));
        int dimensions = 0;
        for (; deep.isArray(); deep = deep.componentType()) {
            dimensions++;
        }
        assertEquals(255, dimensions);
        assertSame(int.class, deep);
        assertSame(Object.class, declared(generated.get("Deepest")));
    }

    @Test
    void aSignatureNestingAnArrayPastTheLimitIsUnreadableWhereverItSits() {
        // Reflection throws UnsupportedOperationException for these, and the class file parser rejects the depth too
        String tooDeep = "[".repeat(256) + "I";
        Generated generated = new Generated()
                .add("Argument", OBJECT + signature(Control.class, tooDeep), "java/lang/Object", internal(Control.class))
                .add("Bound", "<A:" + tooDeep + ">" + OBJECT + signature(Control.class, "TA;"), "java/lang/Object", internal(Control.class))
                .add("Wildcard", OBJECT + signature(Control.class, "+" + tooDeep), "java/lang/Object", internal(Control.class))
                .add("Beside", OBJECT + signature(Supplier.class, tooDeep) + signature(BooleanControl.class), "java/lang/Object", internal(Supplier.class), internal(BooleanControl.class));
        assertThrows(UnsupportedOperationException.class, () -> generated.get("Argument").getGenericInterfaces());
        assertSame(Object.class, declared(generated.get("Argument")));
        assertSame(Object.class, declared(generated.get("Bound")));
        assertSame(Object.class, declared(generated.get("Wildcard")));
        assertSame(Boolean.class, declared(generated.get("Beside")));
    }

    @Test
    void racingThreadsAllGetTheOneAnswer() throws Exception {
        Class<?> fresh = new Generated().add("Raced", signature(Stub.class, "Ljava/lang/Float;"), internal(Stub.class)).get("Raced");
        int threads = 8;
        CyclicBarrier start = new CyclicBarrier(threads);
        Class<?>[] answers = new Class<?>[threads];
        Thread[] running = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            int index = i;
            running[i] = new Thread(() -> {
                try {
                    start.await();
                    answers[index] = declared(fresh);
                } catch (Exception failed) {
                    throw new AssertionError(failed);
                }
            });
            running[i].start();
        }
        for (Thread thread : running) {
            thread.join();
        }
        for (Class<?> answer : answers) {
            assertSame(Float.class, answer);
        }
    }

    @Test
    void boxingCoversEveryPrimitiveAndLeavesOtherClassesAlone() {
        Map<Class<?>, Class<?>> wrappers = Map.of(boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class, char.class, Character.class,
                int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class, void.class, Void.class);
        wrappers.forEach((primitive, wrapper) -> {
            assertSame(wrapper, ControlValueTypes.boxed(primitive));
            assertSame(wrapper, ControlValueTypes.boxed(wrapper));
            assertTrue(ControlValueTypes.related(primitive, wrapper));
            assertTrue(ControlValueTypes.related(wrapper, primitive));
        });
        assertSame(String.class, ControlValueTypes.boxed(String.class));
        assertSame(int[].class, ControlValueTypes.boxed(int[].class));
        assertSame(Mode.class, ControlValueTypes.boxed(Mode.class));
        assertThrows(NullPointerException.class, () -> ControlValueTypes.boxed(null));
    }

    @Test
    void unknownAndEqualTypesAndTypesAlongOneLineAlwaysMatch() {
        assertTrue(ControlValueTypes.related(null, Boolean.class));
        assertTrue(ControlValueTypes.related(Boolean.class, null));
        assertTrue(ControlValueTypes.related(null, null));
        assertTrue(ControlValueTypes.related(Integer.class, Integer.class));
        assertTrue(ControlValueTypes.related(Number.class, Integer.class));
        assertTrue(ControlValueTypes.related(Integer.class, Number.class));
        assertTrue(ControlValueTypes.related(Number.class, int.class));
        assertTrue(ControlValueTypes.related(Comparable.class, int.class));
        assertTrue(ControlValueTypes.related(Serializable.class, boolean.class));
        assertTrue(ControlValueTypes.related(Object.class, Mode.class));
        assertTrue(ControlValueTypes.related(Mode.class, Object.class));
        assertTrue(ControlValueTypes.related(Comparable.class, Bodied.class));
        assertTrue(ControlValueTypes.related(Bodied.class, Bodied.A.getClass()));
        assertTrue(ControlValueTypes.related(CharSequence.class, String.class));
    }

    @Test
    void classesOffEachOthersLineNeverMatch() {
        assertFalse(ControlValueTypes.related(Boolean.class, Integer.class));
        assertFalse(ControlValueTypes.related(Integer.class, Boolean.class));
        assertFalse(ControlValueTypes.related(boolean.class, int.class));
        assertFalse(ControlValueTypes.related(int.class, long.class));
        assertFalse(ControlValueTypes.related(String.class, Mode.class));
        assertFalse(ControlValueTypes.related(Mode.class, Other.class));
        // Abstract or not, a class has one line of superclasses
        assertFalse(ControlValueTypes.related(Number.class, String.class));
        assertFalse(ControlValueTypes.related(AbstractList.class, Thread.class));
        assertFalse(ControlValueTypes.related(Enum.class, Number.class));
    }

    @Test
    void anInterfaceMeetsEveryClassThatIsNeitherFinalNorAnEnum() {
        // An Integer is both, and a subclass of an open class can implement anything
        assertTrue(ControlValueTypes.related(Number.class, Comparable.class));
        assertTrue(ControlValueTypes.related(Comparable.class, Number.class));
        assertTrue(ControlValueTypes.related(Runnable.class, AbstractList.class));
        assertTrue(ControlValueTypes.related(RandomAccess.class, Thread.class));
        assertTrue(ControlValueTypes.related(Runnable.class, Enum.class));
        assertTrue(ControlValueTypes.related(Runnable.class, CharSequence.class));
        assertTrue(ControlValueTypes.related(Runnable.class, Spread.class));
        // A final class, a record and an enum have only the interfaces they declare
        assertFalse(ControlValueTypes.related(CharSequence.class, Integer.class));
        assertFalse(ControlValueTypes.related(Runnable.class, String.class));
        assertFalse(ControlValueTypes.related(Runnable.class, Circle.class));
        assertFalse(ControlValueTypes.related(Runnable.class, Mode.class));
        assertFalse(ControlValueTypes.related(Mode.class, Runnable.class));
        assertFalse(ControlValueTypes.related(Runnable.class, Bodied.class));
        assertFalse(ControlValueTypes.related(Runnable.class, Bodied.A.getClass()));
        assertFalse(ControlValueTypes.related(CharSequence.class, int.class));
    }

    @Test
    void aSealedTypeMeetsOnlyThroughTheSubtypesItPermits() {
        assertFalse(ControlValueTypes.related(Runnable.class, Shape.class));
        assertFalse(ControlValueTypes.related(Shape.class, Runnable.class));
        assertTrue(ControlValueTypes.related(Runnable.class, Loose.class));
        assertTrue(ControlValueTypes.related(Runnable.class, Vehicle.class));
        assertTrue(ControlValueTypes.related(Vehicle.class, Runnable.class));
        assertFalse(ControlValueTypes.related(CharSequence.class, Vehicle.class));
        // Down through sealed subtypes of sealed types, and between two sealed types
        assertTrue(ControlValueTypes.related(Runnable.class, Tree.class));
        assertFalse(ControlValueTypes.related(CharSequence.class, Tree.class));
        assertTrue(ControlValueTypes.related(Left.class, Right.class));
        assertTrue(ControlValueTypes.related(Right.class, Left.class));
        assertFalse(ControlValueTypes.related(Shape.class, Left.class));
        assertFalse(ControlValueTypes.related(Tree.class, Shape.class));
    }

    @Test
    void arraysMeetOnlyArraysWhoseComponentsMeet() {
        assertTrue(ControlValueTypes.related(Number[].class, Integer[].class));
        assertTrue(ControlValueTypes.related(Runnable[].class, Number[].class));
        assertTrue(ControlValueTypes.related(Comparable[][].class, Number[][].class));
        assertTrue(ControlValueTypes.related(Object.class, int[].class));
        assertTrue(ControlValueTypes.related(Cloneable.class, int[].class));
        assertTrue(ControlValueTypes.related(Serializable[].class, int[][].class));
        assertTrue(ControlValueTypes.related(Object[].class, int[][].class));
        assertFalse(ControlValueTypes.related(Integer[].class, Boolean[].class));
        assertFalse(ControlValueTypes.related(Runnable[].class, String[].class));
        // Primitive components are never boxed, and an int[] is no Object[]
        assertFalse(ControlValueTypes.related(int[].class, Integer[].class));
        assertFalse(ControlValueTypes.related(int[].class, long[].class));
        assertFalse(ControlValueTypes.related(Object[].class, int[].class));
        assertFalse(ControlValueTypes.related(Runnable.class, int[].class));
        assertFalse(ControlValueTypes.related(Runnable[][].class, Number[].class));
        assertFalse(ControlValueTypes.related(Comparable[].class, Number[][].class));
    }

    @Test
    void theMatchIsSymmetricAndNeverRejectsTypesOneValueHolds() {
        List<Class<?>> types = List.of(Object.class, Number.class, Integer.class, Long.class, int.class, long.class, boolean.class, Boolean.class, String.class,
                CharSequence.class, Comparable.class, Runnable.class, Serializable.class, Cloneable.class, RandomAccess.class, StringBuilder.class,
                AbstractList.class, ArrayList.class, List.class, Thread.class, Mode.class, Other.class, Bodied.class, Bodied.A.getClass(), Enum.class,
                Shape.class, Circle.class, Square.class, Loose.class, Spread.class, Vehicle.class, Runner.class, Plain.class, Tree.class, Branch.class,
                Twig.class, Left.class, Right.class, Middle.class, int[].class, long[].class, Integer[].class, Number[].class, Object[].class,
                Runnable[].class, String[].class, CharSequence[].class, Comparable[].class, Serializable[].class, int[][].class, Object[][].class);
        Runnable lambda = () -> { };
        List<Object> values = List.of(1, 1L, true, "s", new StringBuilder(), new ArrayList<>(), List.of(), new RunnableList(), Thread.currentThread(),
                Mode.A, Other.X, Bodied.A, Bodied.B, new Circle(), new Square(), new Spread(), new Runner(), new Plain(), new Twig(), new Middle(),
                new LeftOnly(), new RightOnly(), lambda, new Object(), new int[0], new long[0], new Integer[0], new Number[0], new Object[0],
                new Runnable[0], new String[0], new CharSequence[0], new int[0][], new Object[0][]);
        for (Class<?> a : types) {
            for (Class<?> b : types) {
                boolean related = ControlValueTypes.related(a, b);
                assertEquals(related, ControlValueTypes.related(b, a), () -> a + " and " + b);
                for (Object value : values) {
                    if (ControlValueTypes.boxed(a).isInstance(value) && ControlValueTypes.boxed(b).isInstance(value)) {
                        assertTrue(related, () -> a + " and " + b + " both hold " + value);
                    }
                }
            }
        }
    }
}
