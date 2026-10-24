package com.bdmajora.impetus.engine.impl.asm;

import com.bdmajora.impetus.engine.impl.asm.elsewhere.Elsewhere;
import com.bdmajora.impetus.engine.impl.asm.elsewhere.Plain;
import com.bdmajora.testing.FreshClasses;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ProxyClassGeneratorTest {
    // every proxy is a permanent class in the test JVM, so each one gets a name of its own
    private static final AtomicInteger SERIAL = new AtomicInteger();

    // an uninstrumented copy of the generator, whose proxies JaCoCo skips too: its probes raise every max stack and would hide one too small
    private static final FreshClasses UNINSTRUMENTED = new FreshClasses(ProxyClassGenerator.class);

    // what SlotsImpl.edge answers for the arguments 1..127
    private static final long EDGE = 1_064_127L;

    public interface Greeter {
        String greet(String name, int times);

        String greet(String name);

        long widen(int a, long b, double c);

        void nothing();
    }

    public static class Impl implements Greeter {
        int calls;

        @Override
        public String greet(String name, int times) {
            return name.repeat(times);
        }

        @Override
        public String greet(String name) {
            return "hi " + name;
        }

        @Override
        public long widen(int a, long b, double c) {
            return a + b + (long) c;
        }

        @Override
        public void nothing() {
            calls++;
        }

        // public but on no interface
        public int extra() {
            return 5;
        }

        public static void ignored() {
        }
    }

    // inherits Greeter without listing it
    public static class Child extends Impl {
    }

    public interface Primitives {
        boolean z();

        byte b();

        char c();

        short s();

        int i();

        long j();

        float f();

        double d();

        Object l();

        int[] a();

        void v();

        String all(boolean z, byte b, char c, short s, int i, long j, float f, double d, Object l, int[] a);

        int[] echo(int[] array);

        double mixed(long j, int i, double d, float f);

        long sum(long... values);

        <T> T pick(T first, T second);
    }

    public static class PrimitivesImpl implements Primitives {
        int ran;

        @Override
        public boolean z() {
            return true;
        }

        @Override
        public byte b() {
            return Byte.MIN_VALUE;
        }

        @Override
        public char c() {
            return Character.MAX_VALUE;
        }

        @Override
        public short s() {
            return Short.MIN_VALUE;
        }

        @Override
        public int i() {
            return Integer.MIN_VALUE;
        }

        @Override
        public long j() {
            return Long.MIN_VALUE;
        }

        @Override
        public float f() {
            return -0f;
        }

        @Override
        public double d() {
            return -Double.MAX_VALUE;
        }

        @Override
        public Object l() {
            return null;
        }

        @Override
        public int[] a() {
            return new int[]{1, 2};
        }

        @Override
        public void v() {
            ran++;
        }

        @Override
        public String all(boolean z, byte b, char c, short s, int i, long j, float f, double d, Object l, int[] a) {
            return z + "," + b + "," + (int) c + "," + s + "," + i + "," + j + "," + f + "," + d + "," + l + "," + Arrays.toString(a);
        }

        @Override
        public int[] echo(int[] array) {
            return array;
        }

        // positional weights, so arguments loaded from the wrong slots give a different answer
        @Override
        public double mixed(long j, int i, double d, float f) {
            return j * 1000.0 + i * 100.0 + d * 10.0 + f;
        }

        @Override
        public long sum(long... values) {
            return LongStream.of(values).sum();
        }

        @Override
        public <T> T pick(T first, T second) {
            return second;
        }
    }

    // 127 longs plus the receiver fill all 255 argument slots a method may have
    public interface Slots {
        long edge(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7, long a8, long a9, long a10, long a11, long a12, long a13, long a14, long a15, long a16, long a17, long a18, long a19, long a20, long a21, long a22, long a23, long a24, long a25, long a26, long a27, long a28, long a29, long a30, long a31, long a32, long a33, long a34, long a35, long a36, long a37, long a38, long a39, long a40, long a41, long a42, long a43, long a44, long a45, long a46, long a47, long a48, long a49, long a50, long a51, long a52, long a53, long a54, long a55, long a56, long a57, long a58, long a59, long a60, long a61, long a62, long a63, long a64, long a65, long a66, long a67, long a68, long a69, long a70, long a71, long a72, long a73, long a74, long a75, long a76, long a77, long a78, long a79, long a80, long a81, long a82, long a83, long a84, long a85, long a86, long a87, long a88, long a89, long a90, long a91, long a92, long a93, long a94, long a95, long a96, long a97, long a98, long a99, long a100, long a101, long a102, long a103, long a104, long a105, long a106, long a107, long a108, long a109, long a110, long a111, long a112, long a113, long a114, long a115, long a116, long a117, long a118, long a119, long a120, long a121, long a122, long a123, long a124, long a125, long a126);
    }

    public static class SlotsImpl implements Slots {
        @Override
        public long edge(long a0, long a1, long a2, long a3, long a4, long a5, long a6, long a7, long a8, long a9, long a10, long a11, long a12, long a13, long a14, long a15, long a16, long a17, long a18, long a19, long a20, long a21, long a22, long a23, long a24, long a25, long a26, long a27, long a28, long a29, long a30, long a31, long a32, long a33, long a34, long a35, long a36, long a37, long a38, long a39, long a40, long a41, long a42, long a43, long a44, long a45, long a46, long a47, long a48, long a49, long a50, long a51, long a52, long a53, long a54, long a55, long a56, long a57, long a58, long a59, long a60, long a61, long a62, long a63, long a64, long a65, long a66, long a67, long a68, long a69, long a70, long a71, long a72, long a73, long a74, long a75, long a76, long a77, long a78, long a79, long a80, long a81, long a82, long a83, long a84, long a85, long a86, long a87, long a88, long a89, long a90, long a91, long a92, long a93, long a94, long a95, long a96, long a97, long a98, long a99, long a100, long a101, long a102, long a103, long a104, long a105, long a106, long a107, long a108, long a109, long a110, long a111, long a112, long a113, long a114, long a115, long a116, long a117, long a118, long a119, long a120, long a121, long a122, long a123, long a124, long a125, long a126) {
            return a0 * 1_000_000 + a63 * 1_000 + a126;
        }
    }

    public interface Defaults {
        String name();

        default String who() {
            return "default " + name();
        }

        default Object self() {
            return this;
        }

        static String util() {
            return "static";
        }
    }

    public static class DefaultsKept implements Defaults {
        @Override
        public String name() {
            return "kept";
        }
    }

    public static class DefaultsOverridden implements Defaults {
        @Override
        public String name() {
            return "over";
        }

        @Override
        public String who() {
            return "own";
        }
    }

    public interface Thrower {
        void fail() throws IOException;

        int boom();
    }

    public static class ThrowerImpl implements Thrower {
        final IOException checked = new IOException("checked");
        final IllegalStateException unchecked = new IllegalStateException("unchecked");

        @Override
        public void fail() throws IOException {
            throw checked;
        }

        @Override
        public int boom() {
            throw unchecked;
        }
    }

    public interface Source {
        Object get();
    }

    public interface Sink<T> {
        void put(T value);
    }

    // covariant get and generic put, both reached through javac's bridges
    public static class Narrow implements Source, Sink<String> {
        String last;

        @Override
        public String get() {
            return "narrow";
        }

        @Override
        public void put(String value) {
            last = value;
        }
    }

    public interface Left {
        int shared();
    }

    public interface Right {
        int shared();
    }

    // leaves one signature unimplemented from two unrelated interfaces
    public abstract static class Partial implements Left, Right {
    }

    public interface Top {
        int top();
    }

    public interface Mid extends Top {
        int mid();
    }

    public static class Deep implements Mid {
        @Override
        public int top() {
            return 1;
        }

        @Override
        public int mid() {
            return 2;
        }
    }

    public interface LeftTop extends Top {
    }

    public interface RightTop extends Top {
    }

    // reaches Top along two paths
    public static class Diamond implements LeftTop, RightTop {
        @Override
        public int top() {
            return 3;
        }
    }

    // lists Greeter again although Impl already implements it
    public static class Again extends Impl implements Greeter {
    }

    @FunctionalInterface
    public interface IntOp {
        int apply(int x);
    }

    // package-private, so only a proxy in this runtime package may name it
    static class Local implements IntOp {
        @Override
        public int apply(int x) {
            return x + 1;
        }
    }

    public sealed interface Shape permits Circle {
        double area();
    }

    public static final class Circle implements Shape {
        @Override
        public double area() {
            return 3.0;
        }
    }

    public sealed interface Root permits Branch {
    }

    public non-sealed interface Branch extends Root {
        int leaf();
    }

    public static class Tree implements Branch {
        @Override
        public int leaf() {
            return 7;
        }
    }

    public sealed interface Marker permits Tagged {
    }

    public static final class Tagged implements Marker, IntOp {
        @Override
        public int apply(int x) {
            return -x;
        }
    }

    public interface Coords {
        int x();

        int y();
    }

    public record Point(int x, int y) implements Coords {
    }

    private static String fresh(String stem) {
        return stem + SERIAL.incrementAndGet();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProxyClassGenerator<Object, Object> raw(Class<?> delegate, String name, Class<?> primary) {
        return new ProxyClassGenerator(delegate, name, primary);
    }

    // the members a coverage agent did not add
    private static <M extends Member> List<M> own(M[] members) {
        return Arrays.stream(members).filter(member -> !member.isSynthetic()).toList();
    }

    private static List<String> ownMethodNames(Class<?> type) {
        return own(type.getDeclaredMethods()).stream().map(Method::getName).sorted().toList();
    }

    // builds through the uninstrumented generator copy, throwing whatever its constructor threw
    private static Function<Object, Object> bare(Class<?> delegate, Class<?> primary) throws Throwable {
        Class<?> copy = UNINSTRUMENTED.load(ProxyClassGenerator.class);
        assertNotSame(ProxyClassGenerator.class, copy);
        Object generator;
        try {
            generator = copy.getConstructor(Class.class, String.class, Class.class).newInstance(delegate, fresh("Bare"), primary);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        Method wrap = copy.getMethod("generateWrapper", Object.class);
        return target -> {
            try {
                Object proxy = wrap.invoke(generator, target);
                assertTrue(Arrays.stream(proxy.getClass().getDeclaredMethods()).noneMatch(Method::isSynthetic), "the bare proxy was instrumented");
                return proxy;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        };
    }

    private static long callEdge(Object proxy) throws ReflectiveOperationException {
        return (long) Slots.class.getMethods()[0].invoke(proxy, LongStream.rangeClosed(1, 127).boxed().toArray());
    }

    private static void checkPrimitives(Primitives proxy, PrimitivesImpl impl) {
        int ran = impl.ran;
        assertTrue(proxy.z());
        assertEquals(Byte.MIN_VALUE, proxy.b());
        assertEquals(Character.MAX_VALUE, proxy.c());
        assertEquals(Short.MIN_VALUE, proxy.s());
        assertEquals(Integer.MIN_VALUE, proxy.i());
        assertEquals(Long.MIN_VALUE, proxy.j());
        assertEquals(Float.floatToRawIntBits(-0f), Float.floatToRawIntBits(proxy.f()));
        assertEquals(-Double.MAX_VALUE, proxy.d());
        assertNull(proxy.l());
        assertArrayEquals(new int[]{1, 2}, proxy.a());
        proxy.v();
        assertEquals(ran + 1, impl.ran);
        assertEquals("false,-1,65535,-32768,2147483647,-9223372036854775808,-0.0,4.9E-324,null,[7]",
                proxy.all(false, (byte) -1, '￿', Short.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, -0f, Double.MIN_VALUE, null, new int[]{7}));
        int[] array = {7};
        assertSame(array, proxy.echo(array));
        assertNull(proxy.echo(null));
        assertEquals(4_321.5, proxy.mixed(4L, 3, 2.0, 1.5f));
        assertEquals(0L, proxy.sum());
        assertEquals(6L, proxy.sum(1, 2, 3));
        assertEquals("b", proxy.pick("a", "b"));
    }

    @Test
    void forwardsEveryPublicInstanceMethodOfTheDelegate() throws ReflectiveOperationException {
        ProxyClassGenerator<Impl, Greeter> generator = new ProxyClassGenerator<>(Impl.class, fresh("Greeter"), Greeter.class);
        Impl impl = new Impl();
        Greeter proxy = generator.generateWrapper(impl);
        assertNotSame(impl, proxy);
        assertEquals("abab", proxy.greet("ab", 2));
        assertEquals("", proxy.greet("ab", 0));
        assertEquals("hi ab", proxy.greet("ab"));
        assertEquals(6L, proxy.widen(1, 2L, 3.5));
        assertEquals(Long.MIN_VALUE, proxy.widen(Integer.MIN_VALUE, Long.MIN_VALUE - Integer.MIN_VALUE, -0.9));
        proxy.nothing();
        proxy.nothing();
        assertEquals(2, impl.calls);
        // a public method on no interface is still forwarded, reachable by reflection
        assertEquals(5, proxy.getClass().getMethod("extra").invoke(proxy));
        // static methods and the ones Object declares are not
        assertThrows(NoSuchMethodException.class, () -> proxy.getClass().getMethod("ignored"));
        assertEquals(List.of("extra", "greet", "greet", "nothing", "widen"), ownMethodNames(proxy.getClass()));
        // calls dispatch virtually, so a subclass instance answers with its overrides
        Greeter sub = generator.generateWrapper(new Impl() {
            @Override
            public String greet(String name) {
                return "sub " + name;
            }
        });
        assertEquals("sub x", sub.greet("x"));
        assertEquals("xx", sub.greet("x", 2));
    }

    @Test
    void theProxyIsAFinalClassBesideTheGeneratorHoldingOnlyTheDelegate() {
        String name = fresh("Shape");
        Greeter proxy = new ProxyClassGenerator<>(Impl.class, name, Greeter.class).generateWrapper(new Impl());
        Class<?> type = proxy.getClass();
        assertEquals(ProxyClassGenerator.class.getPackageName() + "." + name, type.getName());
        assertSame(ProxyClassGenerator.class.getClassLoader(), type.getClassLoader());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(type.isInterface() || type.isHidden() || type.isSynthetic());
        assertSame(Object.class, type.getSuperclass());
        assertArrayEquals(new Class<?>[]{Greeter.class}, type.getInterfaces());
        List<Field> fields = own(type.getDeclaredFields());
        assertEquals(1, fields.size());
        assertEquals(Modifier.PRIVATE | Modifier.FINAL, fields.getFirst().getModifiers());
        assertSame(Impl.class, fields.getFirst().getType());
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Modifier.PUBLIC, constructors[0].getModifiers());
        assertArrayEquals(new Class<?>[]{Impl.class}, constructors[0].getParameterTypes());
    }

    @Test
    void everyArgumentAndReturnTypeCrossesUnchanged() throws Throwable {
        PrimitivesImpl impl = new PrimitivesImpl();
        checkPrimitives(new ProxyClassGenerator<>(PrimitivesImpl.class, fresh("Primitives"), Primitives.class).generateWrapper(impl), impl);
        // without JaCoCo's padding a long or double from a call with no arguments needs a stack of two
        checkPrimitives((Primitives) bare(PrimitivesImpl.class, Primitives.class).apply(impl), impl);
    }

    @Test
    void aMethodUsingAll255ArgumentSlotsForwards() throws Throwable {
        SlotsImpl impl = new SlotsImpl();
        assertEquals(EDGE, callEdge(impl));
        assertEquals(EDGE, callEdge(new ProxyClassGenerator<>(SlotsImpl.class, fresh("Slots"), Slots.class).generateWrapper(impl)));
        assertEquals(EDGE, callEdge(bare(SlotsImpl.class, Slots.class).apply(impl)));
        // an interface delegate takes invokeinterface, whose argument count operand is one byte
        assertEquals(EDGE, callEdge(new ProxyClassGenerator<>(Slots.class, fresh("SlotsItf"), Slots.class).generateWrapper(impl)));
        assertEquals(EDGE, callEdge(bare(Slots.class, Slots.class).apply(impl)));
    }

    @Test
    void defaultMethodsRunOnTheDelegate() {
        DefaultsKept kept = new DefaultsKept();
        Defaults proxy = new ProxyClassGenerator<>(DefaultsKept.class, fresh("Kept"), Defaults.class).generateWrapper(kept);
        assertEquals("default kept", proxy.who());
        assertSame(kept, proxy.self());
        Defaults overridden = new ProxyClassGenerator<>(DefaultsOverridden.class, fresh("Over"), Defaults.class).generateWrapper(new DefaultsOverridden());
        assertEquals("own", overridden.who());
        // the interface's static method stays on the interface
        assertFalse(ownMethodNames(proxy.getClass()).contains("util"));
        assertEquals("static", Defaults.util());
    }

    @Test
    void exceptionsPassThroughUnwrapped() {
        ThrowerImpl impl = new ThrowerImpl();
        Thrower proxy = new ProxyClassGenerator<>(ThrowerImpl.class, fresh("Thrower"), Thrower.class).generateWrapper(impl);
        assertSame(impl.checked, assertThrows(IOException.class, proxy::fail));
        assertSame(impl.unchecked, assertThrows(IllegalStateException.class, proxy::boom));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void covariantAndGenericMethodsReachTheDelegateThroughItsBridges() {
        Narrow narrow = new Narrow();
        Source proxy = new ProxyClassGenerator<>(Narrow.class, fresh("Narrow"), Source.class).generateWrapper(narrow);
        assertEquals("narrow", proxy.get());
        Sink<String> sink = (Sink<String>) proxy;
        sink.put("x");
        assertEquals("x", narrow.last);
        // the delegate's bridge still casts, so a raw put of the wrong type fails as it would on the delegate
        Sink rawSink = sink;
        assertThrows(ClassCastException.class, () -> rawSink.put(42));
        assertEquals("x", narrow.last);
    }

    @Test
    void oneSignatureFromTwoUnrelatedInterfacesIsForwardedOnce() {
        Left proxy = new ProxyClassGenerator<>(Partial.class, fresh("Partial"), Left.class).generateWrapper(new Partial() {
            @Override
            public int shared() {
                return 9;
            }
        });
        assertEquals(9, proxy.shared());
        assertEquals(9, ((Right) proxy).shared());
        assertEquals(List.of("shared"), ownMethodNames(proxy.getClass()));
    }

    @Test
    void interfacesFromSuperclassesAndSuperinterfacesAreImplemented() {
        Greeter child = new ProxyClassGenerator<>(Child.class, fresh("Child"), Greeter.class).generateWrapper(new Child());
        assertEquals("hi c", child.greet("c"));
        // the primary interface may be any supertype the delegate has
        Top deep = new ProxyClassGenerator<>(Deep.class, fresh("Deep"), Top.class).generateWrapper(new Deep());
        assertEquals(1, deep.top());
        assertEquals(2, ((Mid) deep).mid());
    }

    @Test
    void anInterfaceReachedTwiceIsListedOnce() {
        Greeter again = new ProxyClassGenerator<>(Again.class, fresh("Again"), Greeter.class).generateWrapper(new Again());
        assertArrayEquals(new Class<?>[]{Greeter.class}, again.getClass().getInterfaces());
        assertEquals("hi a", again.greet("a"));
        Top diamond = new ProxyClassGenerator<>(Diamond.class, fresh("Diamond"), Top.class).generateWrapper(new Diamond());
        assertEquals(List.of(LeftTop.class, RightTop.class, Top.class), List.of(diamond.getClass().getInterfaces()));
        assertEquals(3, diamond.top());
    }

    @Test
    void anInterfaceCanBeTheDelegateType() {
        ProxyClassGenerator<IntOp, IntOp> generator = new ProxyClassGenerator<>(IntOp.class, fresh("IntOp"), IntOp.class);
        IntOp doubler = x -> x * 2;
        IntOp proxy = generator.generateWrapper(doubler);
        assertEquals(42, proxy.apply(21));
        assertNotSame(doubler.getClass(), proxy.getClass());
        assertEquals(-6, generator.generateWrapper(new Tagged()).apply(6));
    }

    @Test
    void aHiddenDelegateClassIsRejected() {
        IntOp lambda = x -> x;
        assertTrue(lambda.getClass().isHidden());
        assertThrows(IllegalArgumentException.class, () -> raw(lambda.getClass(), fresh("Lambda"), IntOp.class));
    }

    @Test
    void sealedInterfacesAreNeverListedByTheProxy() {
        // the primary interface has to be listed, and a sealed one never permits the proxy
        assertThrows(IllegalArgumentException.class, () -> new ProxyClassGenerator<>(Circle.class, fresh("Circle"), Shape.class));
        // one met on the way is skipped and the interfaces around it kept
        Branch branch = new ProxyClassGenerator<>(Tree.class, fresh("Tree"), Branch.class).generateWrapper(new Tree());
        assertEquals(7, branch.leaf());
        assertInstanceOf(Root.class, branch);
        IntOp tagged = new ProxyClassGenerator<>(Tagged.class, fresh("Tagged"), IntOp.class).generateWrapper(new Tagged());
        assertEquals(-3, tagged.apply(3));
        assertFalse(Marker.class.isInstance(tagged));
    }

    @Test
    void typesThisPackageCannotAccess() throws Throwable {
        // a package-private interface of the delegate is not implemented, but its methods are still forwarded
        Elsewhere.Visible both = new ProxyClassGenerator<>(Elsewhere.Both.class, fresh("Both"), Elsewhere.Visible.class).generateWrapper(new Elsewhere.Both());
        assertEquals(1, both.visible());
        assertFalse(Elsewhere.hidden().isInstance(both));
        assertEquals(2, both.getClass().getMethod("hidden").invoke(both));
        // calls name the delegate, so a default declared only by an inaccessible interface still links
        Elsewhere.Exposed through = new ProxyClassGenerator<>(Elsewhere.Through.class, fresh("Through"), Elsewhere.Exposed.class).generateWrapper(new Elsewhere.Through());
        assertEquals(4, Elsewhere.callHidden(through));
        assertEquals(6, Elsewhere.callFallback(through));
        // an inaccessible delegate or primary interface fails up front rather than at the first call
        assertThrows(IllegalArgumentException.class, () -> raw(Elsewhere.secret(), fresh("Secret"), Elsewhere.Visible.class));
        assertThrows(IllegalArgumentException.class, () -> raw(Elsewhere.Both.class, fresh("HiddenPrimary"), Elsewhere.hidden()));
        // package-private is enough within this runtime package, but not from the copy's
        assertEquals(2, new ProxyClassGenerator<>(Local.class, fresh("Local"), IntOp.class).generateWrapper(new Local()).apply(1));
        assertThrows(IllegalArgumentException.class, () -> bare(Local.class, IntOp.class));
    }

    @Test
    void objectMethodsAreForwardedOnlyWhereTheDelegateOverridesThem() {
        Point point = new Point(1, 2);
        Coords proxy = new ProxyClassGenerator<>(Point.class, fresh("Point"), Coords.class).generateWrapper(point);
        assertEquals(1, proxy.x());
        assertEquals(2, proxy.y());
        assertEquals("Point[x=1, y=2]", proxy.toString());
        assertEquals(point.hashCode(), proxy.hashCode());
        // equals is the delegate's own verdict on the argument
        assertEquals(proxy, new Point(1, 2));
        assertNotEquals(proxy, new Point(2, 1));
        Impl impl = new Impl();
        Greeter plain = new ProxyClassGenerator<>(Impl.class, fresh("Plain"), Greeter.class).generateWrapper(impl);
        assertTrue(plain.toString().startsWith(plain.getClass().getName() + "@"));
        assertEquals(System.identityHashCode(plain), plain.hashCode());
        assertTrue(plain.equals(plain));
        assertNotEquals(plain, impl);
    }

    @Test
    void anArrayCanBeTheDelegateType() {
        Cloneable proxy = new ProxyClassGenerator<>(Object[].class, fresh("Array"), Cloneable.class).generateWrapper(new Object[]{1});
        assertInstanceOf(Serializable.class, proxy);
        assertEquals(List.of(), ownMethodNames(proxy.getClass()));
    }

    @Test
    void argumentsAreCheckedUpFront() {
        assertEquals("realClass", assertThrows(NullPointerException.class, () -> new ProxyClassGenerator<>(null, fresh("Null"), Greeter.class)).getMessage());
        assertEquals("proxyClassName", assertThrows(NullPointerException.class, () -> new ProxyClassGenerator<>(Impl.class, null, Greeter.class)).getMessage());
        assertEquals("primaryInterface", assertThrows(NullPointerException.class, () -> new ProxyClassGenerator<>(Impl.class, fresh("Null"), null)).getMessage());
        // the primary interface must be an interface the delegate implements
        assertThrows(IllegalArgumentException.class, () -> raw(Impl.class, fresh("Self"), Impl.class));
        assertThrows(IllegalArgumentException.class, () -> raw(Impl.class, fresh("Unrelated"), Runnable.class));
        assertThrows(IllegalArgumentException.class, () -> raw(Greeter.class, fresh("Narrower"), Defaults.class));
        assertThrows(IllegalArgumentException.class, () -> raw(int.class, fresh("Primitive"), Comparable.class));
        assertThrows(IllegalArgumentException.class, () -> raw(void.class, fresh("Void"), Comparable.class));
    }

    @Test
    void proxyNamesMustBeOneJavaIdentifier() {
        for (String bad : new String[]{"", "1abc", "a.b", "a/b", "a;b", "a[b", "a b", "a-b", "<init>", "\uD800x", "x\uDC00"}) {
            assertThrows(IllegalArgumentException.class, () -> raw(Impl.class, bad, Greeter.class), bad);
        }
        // a dollar sign, an underscore, non-Latin letters and a letter outside the BMP are all identifiers
        for (String stem : new String[]{"$", "_", "a$b", "Prøxy", "Δ", "𝐀"}) {
            String name = fresh(stem);
            Greeter proxy = new ProxyClassGenerator<>(Impl.class, name, Greeter.class).generateWrapper(new Impl());
            assertEquals(ProxyClassGenerator.class.getPackageName() + "." + name, proxy.getClass().getName());
            assertEquals("hi y", proxy.greet("y"));
        }
        // a class name is one constant pool string of at most 65535 bytes, counted in modified UTF-8
        assertThrows(IllegalArgumentException.class, () -> raw(Impl.class, "L".repeat(70_000), Greeter.class));
        assertThrows(IllegalArgumentException.class, () -> raw(Impl.class, "ø".repeat(33_000), Greeter.class));
    }

    @Test
    void aTakenNameFailsAsTheGeneratorsOwnError() {
        String name = fresh("Once");
        new ProxyClassGenerator<>(Impl.class, name, Greeter.class);
        assertInstanceOf(LinkageError.class, assertThrows(RuntimeException.class, () -> new ProxyClassGenerator<>(Impl.class, name, Greeter.class)).getCause());
        // so does a class already loaded in the package, which keeps working
        assertInstanceOf(LinkageError.class, assertThrows(RuntimeException.class, () -> new ProxyClassGenerator<>(Impl.class, "ProxyClassGenerator", Greeter.class)).getCause());
        assertEquals("hi z", new ProxyClassGenerator<>(Impl.class, fresh("After"), Greeter.class).generateWrapper(new Impl()).greet("z"));
    }

    @Test
    void aDelegateThisLoaderResolvesToAnotherClassFailsUpFront() throws ClassNotFoundException {
        Class<?> foreign = new FreshClasses(Plain.class).load(Plain.class);
        assertNotSame(Plain.class, foreign);
        assertTrue(Elsewhere.Visible.class.isAssignableFrom(foreign));
        // the proxy names the delegate, which its own loader finds as the original class
        String name = fresh("Foreign");
        assertThrows(IllegalArgumentException.class, () -> raw(foreign, name, Elsewhere.Visible.class));
        // refused before anything was defined, so the name is still free
        assertEquals(8, new ProxyClassGenerator<>(Plain.class, name, Elsewhere.Visible.class).generateWrapper(new Plain()).visible());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void eachWrapIsAFreshProxyAndNullOrTheWrongTypeIsRefused() {
        ProxyClassGenerator<Impl, Greeter> generator = new ProxyClassGenerator<>(Impl.class, fresh("Wraps"), Greeter.class);
        Impl impl = new Impl();
        Greeter first = generator.generateWrapper(impl);
        Greeter second = generator.generateWrapper(impl);
        assertNotSame(first, second);
        assertSame(first.getClass(), second.getClass());
        first.nothing();
        second.nothing();
        assertEquals(2, impl.calls);
        // a proxy around null would only fail later, on whichever thread first called through it
        assertEquals("delegate", assertThrows(NullPointerException.class, () -> generator.generateWrapper(null)).getMessage());
        ProxyClassGenerator<Object, Greeter> rawGenerator = (ProxyClassGenerator) generator;
        assertInstanceOf(ClassCastException.class, assertThrows(RuntimeException.class, () -> rawGenerator.generateWrapper("not an Impl")).getCause());
    }

    @Test
    void wrapsAndCallsFromManyThreadsAtOnce() {
        ProxyClassGenerator<Impl, Greeter> generator = new ProxyClassGenerator<>(Impl.class, fresh("Threads"), Greeter.class);
        assertEquals(10_000, IntStream.range(0, 10_000).parallel().filter(i -> generator.generateWrapper(new Impl()).greet("x", i % 3).length() == i % 3).count());
    }
}
