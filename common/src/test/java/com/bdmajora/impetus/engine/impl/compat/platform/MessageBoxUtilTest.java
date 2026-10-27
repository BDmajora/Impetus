package com.bdmajora.impetus.engine.impl.compat.platform;

import com.bdmajora.testing.Statics;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

// Tests the logging and the dialogs of MessageBoxUtil
class MessageBoxUtilTest {
    // System property that hides every dialog
    private static final String HIDE = "impetus.hideMessageBoxes";
    // Pattern every message is logged with
    private static final String PATTERN = "{}: {}";
    // Parameter types of the log overload every message goes through
    private static final List<Class<?>> LOG_PARAMETERS = List.of(Level.class, String.class, Object.class, Object.class);
    // Logger that receives the messages of every test
    private static final Logger LOGGER = mock(Logger.class);
    // Constructor of the log parameter that carries each message
    private static final Constructor<?> SINGLE_LINE = singleLineConstructor();
    // Fragments the random messages are joined from
    private static final String[] FRAGMENTS = {
            "a", "Z", " ", "\n", "\r", "\r\n", "\n\r", "\u00E9", "\u0085", "\u2028", "\u2029", "\u000B", "\f", "\t",
            "\uD83D\uDE00", "\uD83D", "\uDE00", "\u0000", "\u4E2D", "line", ""};
    // Largest number of fragments in a random message
    private static final int MAX_FRAGMENTS = 12;
    // Number of random messages compared with the reference
    private static final int RANDOM_MESSAGES = 200_000;
    // Length of the long messages in characters
    private static final int LONG_LENGTH = 1 << 24;
    // Distance between the line breaks of the long message in characters
    private static final int LONG_BREAK_SPACING = 997;
    // Number of calls in the repeated call test
    private static final int REPEATS = 16;
    // Number of threads that log at the same time
    private static final int THREADS = 8;
    // Number of messages each thread logs
    private static final int PER_THREAD = 4;
    // Dialog types at the int limits and around every message type of JOptionPane
    private static final int[] TYPES = {
            Integer.MIN_VALUE, -2, JOptionPane.PLAIN_MESSAGE, JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.WARNING_MESSAGE, JOptionPane.QUESTION_MESSAGE, 4, Integer.MAX_VALUE};

    // Logger of MessageBoxUtil before the tests
    private static Logger realLogger;
    // Value of the hide property before each test
    private String hidden;

    // Installs the recording logger
    @BeforeAll
    static void installLogger() {
        realLogger = Statics.get(MessageBoxUtil.class, "LOGGER");
        Statics.set(MessageBoxUtil.class, "LOGGER", LOGGER);
    }

    // Initialises the Swing and AWT classes outside every static mock
    @BeforeAll
    static void initialiseSwing() throws ClassNotFoundException {
        Class.forName(JOptionPane.class.getName());
    }

    // Restores the logger of MessageBoxUtil
    @AfterAll
    static void restoreLogger() {
        Statics.set(MessageBoxUtil.class, "LOGGER", realLogger);
    }

    // Clears the recorded messages and stores the hide property
    @BeforeEach
    void clear() {
        reset(LOGGER);
        hidden = System.getProperty(HIDE);
    }

    // Restores the hide property
    @AfterEach
    void restoreHide() {
        setHide(hidden);
    }

    // Level and arguments of one logged message
    private record Entry(Level level, Object title, Object line) {
        // Returns the message log4j formats from the arguments
        String rendered() {
            return new ParameterizedMessage(PATTERN, title, line).getFormattedMessage();
        }
    }

    // Returns the accessible constructor of the log parameter class
    private static Constructor<?> singleLineConstructor() {
        try {
            Constructor<?> constructor = Class.forName(MessageBoxUtil.class.getName() + "$SingleLine").getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Returns a new log parameter for the text
    private static StringBuilderFormattable newLine(String text) {
        try {
            return (StringBuilderFormattable) SINGLE_LINE.newInstance(text);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Sets the hide property or clears it when the value is null
    private static void setHide(String value) {
        if (value == null) {
            System.clearProperty(HIDE);
        } else {
            System.setProperty(HIDE, value);
        }
    }

    // Returns every message logged since the last reset in call order
    private static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Invocation call : mockingDetails(LOGGER).getInvocations()) {
            assertEquals("log", call.getMethod().getName());
            assertEquals(LOG_PARAMETERS, List.of(call.getMethod().getParameterTypes()));
            Object[] arguments = call.getArguments();
            assertEquals(PATTERN, arguments[1]);
            entries.add(new Entry((Level) arguments[0], arguments[2], arguments[3]));
        }
        return entries;
    }

    // Returns the one message logged since the last reset
    private static Entry only() {
        List<Entry> entries = entries();
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    // Returns the text the parameter renders after checking that formatting and toString agree
    private static String render(Object line) {
        StringBuilderFormattable formattable = assertInstanceOf(StringBuilderFormattable.class, line);
        StringBuilder buffer = new StringBuilder();
        formattable.formatTo(buffer);
        String text = line.toString();
        assertEquals(text, buffer.toString());
        return text;
    }

    // Returns the line a warning logs for the message after checking that every rendering of it agrees
    private static String logged(String message) {
        reset(LOGGER);
        MessageBoxUtil.showWarning("t", message);
        Entry entry = only();
        String text = render(entry.line());
        assertEquals("t: " + text, entry.rendered());
        return text;
    }

    // Returns the text with every carriage return line feed pair and every other line break replaced by one space
    private static String reference(String text) {
        return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
    }

    // Runs the action with mocked dialogs and a display that is headless or not
    private static void withDisplay(boolean headless, BiConsumer<MockedStatic<GraphicsEnvironment>, MockedStatic<JOptionPane>> action) {
        try (MockedStatic<GraphicsEnvironment> graphics = mockStatic(GraphicsEnvironment.class);
             MockedStatic<JOptionPane> dialogs = mockStatic(JOptionPane.class)) {
            graphics.when(GraphicsEnvironment::isHeadless).thenReturn(headless);
            action.accept(graphics, dialogs);
        }
    }

    // Checks that the class is final and has one private constructor that creates an instance
    @Test
    void classIsFinalWithOnePrivateConstructor() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(MessageBoxUtil.class.getModifiers()));
        Constructor<?>[] constructors = MessageBoxUtil.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        constructors[0].setAccessible(true);
        assertInstanceOf(MessageBoxUtil.class, constructors[0].newInstance());
    }

    // Checks that a warning logs at the warn level with the title and the message
    @Test
    void warningLogsAtWarnLevel() {
        MessageBoxUtil.showWarning("Title", "body");
        Entry entry = only();
        assertEquals(Level.WARN, entry.level());
        assertEquals("Title", entry.title());
        assertSame(SINGLE_LINE.getDeclaringClass(), entry.line().getClass());
        assertEquals("Title: body", entry.rendered());
    }

    // Checks that an error logs at the error level with the title and the message
    @Test
    void errorLogsAtErrorLevel() {
        MessageBoxUtil.showError("Title", "body");
        Entry entry = only();
        assertEquals(Level.ERROR, entry.level());
        assertEquals("Title", entry.title());
        assertEquals("Title: body", entry.rendered());
    }

    // Checks that only the error type logs at the error level for every type including the int limits
    @Test
    void levelFollowsTheDialogType() {
        for (int type : TYPES) {
            reset(LOGGER);
            Statics.call(MessageBoxUtil.class, "show", "t", "m", type);
            assertEquals(type == JOptionPane.ERROR_MESSAGE ? Level.ERROR : Level.WARN, only().level(), "type " + type);
        }
    }

    // Checks that line feeds become spaces
    @Test
    void lineFeedsBecomeSpaces() {
        assertEquals("line one line two", logged("line one\nline two"));
        assertEquals("a  b", logged("a\n\nb"));
    }

    // Checks that a carriage return line feed pair becomes one space
    @Test
    void carriageReturnLineFeedBecomesOneSpace() {
        assertEquals("a b", logged("a\r\nb"));
        assertEquals("a  b", logged("a\r\n\r\nb"));
    }

    // Checks that a carriage return without a following line feed becomes a space
    @Test
    void loneCarriageReturnBecomesSpace() {
        assertEquals("a b", logged("a\rb"));
        assertEquals("a  b", logged("a\r\rb"));
        assertEquals("a  b", logged("a\n\rb"));
        assertEquals("a  b", logged("a\r\r\nb"));
    }

    // Checks breaks at the first and last index and messages made only of breaks
    @Test
    void breaksAtTheBoundaries() {
        String[][] cases = {
                {"\nx", " x"}, {"x\n", "x "}, {"\rx", " x"}, {"x\r", "x "}, {"\r\nx", " x"}, {"x\r\n", "x "},
                {"\n", " "}, {"\r", " "}, {"\r\n", " "}, {"\n\r", "  "}, {"\n\n", "  "}, {"\r\r", "  "},
                {"\r\n\n", "  "}, {"\n\r\n", "  "}, {"\r\n\r", "  "}, {"\n\n\n", "   "}};
        for (String[] pair : cases) {
            assertEquals(pair[1], render(newLine(pair[0])), pair[0]);
        }
        assertEquals(" x ", logged("\r\nx\r"));
    }

    // Checks that a message without line breaks renders as the same string
    @Test
    void messageWithoutBreaksRendersAsIs() {
        for (String message : new String[]{"plain", "", "a", "\u00E9\u4E2D\uD83D\uDE00", "\u0085\u2028"}) {
            assertSame(message, newLine(message).toString());
        }
        MessageBoxUtil.showWarning("t", "plain");
        assertEquals("plain", render(only().line()));
    }

    // Checks the empty message and the empty title
    @Test
    void emptyMessageAndTitle() {
        assertEquals("", logged(""));
        reset(LOGGER);
        MessageBoxUtil.showError("Title", "");
        assertEquals("Title: ", only().rendered());
        reset(LOGGER);
        MessageBoxUtil.showWarning("", "m");
        assertEquals(": m", only().rendered());
        reset(LOGGER);
        MessageBoxUtil.showWarning("", "");
        assertEquals(": ", only().rendered());
    }

    // Checks messages of a single character
    @Test
    void singleCharacterMessages() {
        assertEquals("a", logged("a"));
        assertEquals(" ", logged("\n"));
        assertEquals(" ", logged("\r"));
        assertEquals("\u0000", logged("\u0000"));
        assertEquals("\uD83D", logged("\uD83D"));
    }

    // Checks that a null message logs as null
    @Test
    void nullMessageLogsNull() {
        assertEquals("null", logged(null));
        reset(LOGGER);
        assertDoesNotThrow(() -> MessageBoxUtil.showError("Title", null));
        Entry entry = only();
        assertEquals(Level.ERROR, entry.level());
        assertEquals("Title: null", entry.rendered());
    }

    // Checks that a null title logs as null
    @Test
    void nullTitleLogsNull() {
        MessageBoxUtil.showError(null, "m");
        Entry entry = only();
        assertNull(entry.title());
        assertEquals("null: m", entry.rendered());
    }

    // Checks that a null title and a null message log as null
    @Test
    void nullTitleAndMessageLogNull() {
        assertDoesNotThrow(() -> MessageBoxUtil.showWarning(null, null));
        assertEquals("null: null", only().rendered());
    }

    // Checks that the title keeps its line breaks
    @Test
    void titleKeepsLineBreaks() {
        MessageBoxUtil.showWarning("a\r\nb", "c\nd");
        assertEquals("a\r\nb: c d", only().rendered());
    }

    // Checks that placeholders and escapes of the log pattern in the title and the message render literally
    @Test
    void placeholdersRenderLiterally() {
        MessageBoxUtil.showWarning("{}", "a {} b\n\\{} {}");
        assertEquals("{}: a {} b \\{} {}", only().rendered());
        reset(LOGGER);
        MessageBoxUtil.showError("\\", "${env:HOME}\r\n%s %n");
        assertEquals("\\: ${env:HOME} %s %n", only().rendered());
    }

    // Checks that other separators and control characters stay in the line
    @Test
    void otherSeparatorsStayInTheLine() {
        for (String separator : new String[]{"\u0085", "\u2028", "\u2029", "\u000B", "\f", "\t", "\u0000", "\u001C", "\u001E"}) {
            String message = "a" + separator + "b";
            assertEquals(message, logged(message));
        }
    }

    // Checks that surrogates and characters outside Latin 1 next to breaks are kept
    @Test
    void surrogatesAroundBreaksAreKept() {
        assertEquals("\uD83D\uDE00 \uD83D\uDE00", logged("\uD83D\uDE00\n\uD83D\uDE00"));
        assertEquals("\uD83D \uDE00", logged("\uD83D\r\n\uDE00"));
        assertEquals("\u4E2D \u00E9", logged("\u4E2D\r\u00E9"));
        assertEquals("\u00E9 \u00FF ", logged("\u00E9\r\n\u00FF\n"));
    }

    // Checks that formatting appends after existing buffer content and appends again on every call
    @Test
    void formatToAppendsOnEveryCall() {
        MessageBoxUtil.showWarning("t", "a\r\nb\nc");
        StringBuilderFormattable line = assertInstanceOf(StringBuilderFormattable.class, only().line());
        StringBuilder buffer = new StringBuilder("prefix|");
        line.formatTo(buffer);
        line.formatTo(buffer);
        assertEquals("prefix|a b ca b c", buffer.toString());
        assertEquals("a b c", line.toString());
        assertEquals("a b c", line.toString());
    }

    // Checks random messages against the reference
    @Test
    void randomMessagesMatchReference() {
        Random random = new Random(0x4D42L);
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < RANDOM_MESSAGES; i++) {
            message.setLength(0);
            for (int count = random.nextInt(MAX_FRAGMENTS + 1); count > 0; count--) {
                message.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]);
            }
            String text = message.toString();
            StringBuilderFormattable line = newLine(text);
            String expected = reference(text);
            assertEquals(expected, render(line), text);
            assertEquals("t: " + expected, new ParameterizedMessage(PATTERN, "t", line).getFormattedMessage());
            if (expected.equals(text)) {
                assertSame(text, line.toString());
            }
        }
    }

    // Checks messages of millions of characters with breaks at the boundaries and throughout
    @Test
    void longMessages() {
        char[] chars = new char[LONG_LENGTH];
        Arrays.fill(chars, 'a');
        String plain = new String(chars);
        assertSame(plain, newLine(plain).toString());
        assertEquals(plain, render(newLine(plain)));

        chars[0] = '\n';
        chars[LONG_LENGTH - 1] = '\r';
        for (int i = LONG_BREAK_SPACING; i < LONG_LENGTH - 2; i += LONG_BREAK_SPACING) {
            chars[i] = '\r';
            chars[i + 1] = '\n';
        }
        String broken = new String(chars);
        assertEquals(reference(broken), render(newLine(broken)));

        for (int i = 0; i < LONG_LENGTH; i += 2) {
            chars[i] = '\r';
            chars[i + 1] = '\n';
        }
        String pairs = new String(chars);
        assertEquals(" ".repeat(LONG_LENGTH / 2), render(newLine(pairs)));

        Arrays.fill(chars, '\n');
        assertEquals(" ".repeat(LONG_LENGTH), logged(new String(chars)));
    }

    // Checks that repeated calls each log their own message in call order
    @Test
    void repeatedCallsLogInOrder() {
        for (int i = 0; i < REPEATS; i++) {
            if ((i & 1) == 0) {
                MessageBoxUtil.showWarning("w" + i, i + "\n" + i);
            } else {
                MessageBoxUtil.showError("e" + i, i + "\r\n" + i);
            }
        }
        List<Entry> entries = entries();
        assertEquals(REPEATS, entries.size());
        for (int i = 0; i < REPEATS; i++) {
            Entry entry = entries.get(i);
            boolean warning = (i & 1) == 0;
            assertEquals(warning ? Level.WARN : Level.ERROR, entry.level());
            assertEquals((warning ? "w" : "e") + i + ": " + i + " " + i, entry.rendered());
        }
    }

    // Checks that threads logging at the same time each log every message
    @Test
    void concurrentCallsLogEveryMessage() throws InterruptedException {
        setHide("true");
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int id = t;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                        MessageBoxUtil.showWarning("t" + id, id + "\r\n" + i);
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(List.of(), failures);
        Set<String> expected = new HashSet<>();
        for (int t = 0; t < THREADS; t++) {
            for (int i = 0; i < PER_THREAD; i++) {
                expected.add("t" + t + ": " + t + " " + i);
            }
        }
        Set<String> rendered = new HashSet<>();
        for (Entry entry : entries()) {
            rendered.add(entry.rendered());
        }
        assertEquals(THREADS * PER_THREAD, entries().size());
        assertEquals(expected, rendered);
    }

    // Checks that every case of true hides the dialog without probing the display
    @Test
    void hidePropertyHidesDialog() {
        for (String value : new String[]{"true", "TRUE", "True", "tRuE"}) {
            setHide(value);
            withDisplay(false, (graphics, dialogs) -> {
                MessageBoxUtil.showWarning("t", "m");
                graphics.verifyNoInteractions();
                dialogs.verifyNoInteractions();
            });
        }
        assertEquals(4, entries().size());
    }

    // Checks that every other value of the hide property shows the dialog
    @Test
    void otherHideValuesShowDialog() {
        for (String value : new String[]{null, "", "false", "yes", "1", "on", " true", "true ", "truee"}) {
            setHide(value);
            withDisplay(false, (graphics, dialogs) -> {
                MessageBoxUtil.showWarning("t", "m");
                dialogs.verify(() -> JOptionPane.showMessageDialog(null, "m", "t", JOptionPane.WARNING_MESSAGE));
                dialogs.verifyNoMoreInteractions();
            });
        }
    }

    // Checks that a headless display skips the dialog after logging
    @Test
    void headlessDisplaySkipsDialog() {
        setHide(null);
        withDisplay(true, (graphics, dialogs) -> {
            MessageBoxUtil.showError("t", "m");
            graphics.verify(GraphicsEnvironment::isHeadless);
            dialogs.verifyNoInteractions();
        });
        assertTrue(GraphicsEnvironment.isHeadless());
        try (MockedStatic<JOptionPane> dialogs = mockStatic(JOptionPane.class)) {
            MessageBoxUtil.showWarning("t", "m");
            dialogs.verifyNoInteractions();
        }
        assertEquals(2, entries().size());
    }

    // Checks that the dialog receives the raw message and the title and the type with no parent
    @Test
    void dialogReceivesRawArguments() {
        setHide(null);
        withDisplay(false, (graphics, dialogs) -> {
            MessageBoxUtil.showWarning("Title", "line one\r\nline two");
            MessageBoxUtil.showError("Error", "");
            dialogs.verify(() -> JOptionPane.showMessageDialog(null, "line one\r\nline two", "Title", JOptionPane.WARNING_MESSAGE));
            dialogs.verify(() -> JOptionPane.showMessageDialog(null, "", "Error", JOptionPane.ERROR_MESSAGE));
            dialogs.verifyNoMoreInteractions();
        });
    }

    // Checks that the dialog receives null arguments unchanged
    @Test
    void dialogReceivesNullArguments() {
        setHide(null);
        withDisplay(false, (graphics, dialogs) -> {
            assertDoesNotThrow(() -> MessageBoxUtil.showError(null, null));
            dialogs.verify(() -> JOptionPane.showMessageDialog(null, null, null, JOptionPane.ERROR_MESSAGE));
            dialogs.verifyNoMoreInteractions();
        });
        assertEquals("null: null", only().rendered());
    }

    // Checks that the dialog receives every type unchanged including the int limits
    @Test
    void dialogReceivesEveryType() {
        setHide(null);
        withDisplay(false, (graphics, dialogs) -> {
            for (int type : TYPES) {
                Statics.call(MessageBoxUtil.class, "show", "t", "m", type);
                dialogs.verify(() -> JOptionPane.showMessageDialog(null, "m", "t", type));
            }
            dialogs.verifyNoMoreInteractions();
        });
    }

    // Checks that the message is logged before the dialog opens
    @Test
    void messageIsLoggedBeforeTheDialog() {
        setHide(null);
        List<Integer> loggedBefore = new ArrayList<>();
        withDisplay(false, (graphics, dialogs) -> {
            dialogs.when(() -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt())).thenAnswer(call -> {
                loggedBefore.add(mockingDetails(LOGGER).getInvocations().size());
                return null;
            });
            MessageBoxUtil.showWarning("t", "m");
        });
        assertEquals(List.of(1), loggedBefore);
    }

    // Checks that repeated calls open one dialog each
    @Test
    void repeatedCallsOpenOneDialogEach() {
        setHide(null);
        withDisplay(false, (graphics, dialogs) -> {
            for (int i = 0; i < 3; i++) {
                MessageBoxUtil.showWarning("w", "m");
                MessageBoxUtil.showError("e", "m");
            }
            dialogs.verify(() -> JOptionPane.showMessageDialog(null, "m", "w", JOptionPane.WARNING_MESSAGE), times(3));
            dialogs.verify(() -> JOptionPane.showMessageDialog(null, "m", "e", JOptionPane.ERROR_MESSAGE), times(3));
            dialogs.verifyNoMoreInteractions();
        });
        assertEquals(6, entries().size());
    }

    // Checks that every failure of the dialog is ignored after the message is logged
    @Test
    void dialogFailuresAreIgnored() {
        setHide(null);
        Throwable[] failures = {
                new HeadlessException(), new IllegalStateException("no display"), new UnsatisfiedLinkError("awt"),
                new NoClassDefFoundError("javax/swing/JOptionPane"), new StackOverflowError(), new OutOfMemoryError("dialog"),
                new AssertionError("dialog")};
        for (Throwable failure : failures) {
            withDisplay(false, (graphics, dialogs) -> {
                dialogs.when(() -> JOptionPane.showMessageDialog(any(), any(), any(), anyInt())).thenThrow(failure);
                assertDoesNotThrow(() -> MessageBoxUtil.showError("t", "m"));
                dialogs.verify(() -> JOptionPane.showMessageDialog(null, "m", "t", JOptionPane.ERROR_MESSAGE));
            });
        }
        assertEquals(failures.length, entries().size());
    }

    // Checks that a failure of the headless probe is ignored after the message is logged
    @Test
    void headlessProbeFailureIsIgnored() {
        setHide(null);
        Throwable[] failures = {
                new NoClassDefFoundError("java/awt/GraphicsEnvironment"), new InternalError("awt"),
                new UnsatisfiedLinkError("awt"), new IllegalStateException("probe")};
        for (Throwable failure : failures) {
            withDisplay(false, (graphics, dialogs) -> {
                graphics.when(GraphicsEnvironment::isHeadless).thenThrow(failure);
                assertDoesNotThrow(() -> MessageBoxUtil.showWarning("t", "m"));
                dialogs.verifyNoInteractions();
            });
        }
        assertEquals(failures.length, entries().size());
    }
}
