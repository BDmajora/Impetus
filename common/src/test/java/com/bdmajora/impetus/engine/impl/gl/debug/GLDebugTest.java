package com.bdmajora.impetus.engine.impl.gl.debug;

import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.FreshClasses;
import com.bdmajora.testing.Statics;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Covers the debug switch and labels and debug groups of GLDebug against the TestGl driver
class GLDebugTest {
    // System property that turns debugging on
    private static final String PROPERTY = "impetus.enableGLDebug";
    // Smallest label length a driver may report
    private static final int LABEL_LENGTH = 256;
    // Message length typical drivers report
    private static final int MESSAGE_LENGTH = 1024;
    // Smallest group stack depth a driver may report
    private static final int STACK_DEPTH = 64;
    // Groups that fit under the smallest stack depth
    private static final int GROUPS = STACK_DEPTH - 2;
    // Integer values passed through to the driver unchanged
    private static final int[] EXTREMES = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};

    // Property value present before each test
    private String before;

    // Stores the property and clears it
    @BeforeEach
    void saveProperty() {
        this.before = System.getProperty(PROPERTY);
        System.clearProperty(PROPERTY);
    }

    // Restores the property and turns debugging off
    @AfterEach
    void restoreProperty() {
        if (this.before == null) System.clearProperty(PROPERTY);
        else System.setProperty(PROPERTY, this.before);
        GLDebug.reloadDebugState();
    }

    // Returns the shared TestGl driver mock
    private static LWJGLService gl() {
        return TestGl.gl();
    }

    // Turns debugging on with the given driver limits and clears recorded calls
    private static void enable(int labelLength, int messageLength, int stackDepth) {
        System.setProperty(PROPERTY, "true");
        when(gl().glGetInteger(GL_MAX_LABEL_LENGTH)).thenReturn(labelLength);
        when(gl().glGetInteger(GL_MAX_DEBUG_MESSAGE_LENGTH)).thenReturn(messageLength);
        when(gl().glGetInteger(GL_MAX_DEBUG_GROUP_STACK_DEPTH)).thenReturn(stackDepth);
        GLDebug.reloadDebugState();
        clearInvocations(gl());
    }

    // Turns debugging on with the smallest limits the spec allows for labels and depth
    private static void enable() {
        enable(LABEL_LENGTH, MESSAGE_LENGTH, STACK_DEPTH);
    }

    // Returns the label the driver receives for a name
    private static String labelled(String name) {
        clearInvocations(gl());
        GLDebug.nameObject(GL_BUFFER, 7, name);
        ArgumentCaptor<CharSequence> label = ArgumentCaptor.forClass(CharSequence.class);
        verify(gl()).glObjectLabel(eq(GL_BUFFER), eq(7), label.capture());
        return (String) label.getValue();
    }

    // Returns the message the driver receives for a group name and pops the group
    private static String pushed(String name) {
        clearInvocations(gl());
        GLDebug.pushGroup(5, name);
        ArgumentCaptor<CharSequence> message = ArgumentCaptor.forClass(CharSequence.class);
        verify(gl()).glPushDebugGroup(eq(GL_DEBUG_SOURCE_APPLICATION), eq(5), message.capture());
        GLDebug.popGroup();
        return (String) message.getValue();
    }

    // Returns the UTF 8 length of text as the JDK encoder writes it
    private static int utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    // Asserts that cut is the longest prefix of name within limit bytes that keeps surrogate pairs whole
    private static void assertLongestFit(String name, String cut, int limit) {
        assertTrue(name.startsWith(cut));
        assertTrue(utf8(cut) <= limit, () -> utf8(cut) + " bytes over " + limit);
        if (cut.length() < name.length()) {
            int next = cut.length() + Character.charCount(name.codePointAt(cut.length()));
            assertTrue(utf8(name.substring(0, next)) > limit);
            assertFalse(!cut.isEmpty() && Character.isHighSurrogate(cut.charAt(cut.length() - 1)) && Character.isLowSurrogate(name.charAt(cut.length())));
        }
    }

    // Calls the private fit helper
    private static String fit(String text, int limit) {
        return Statics.call(GLDebug.class, "fit", text, limit);
    }

    // Checks that the class is final and its only constructor is private and runs
    @Test
    void constructorIsPrivateAndClassIsFinal() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(GLDebug.class.getModifiers()));
        Constructor<?>[] constructors = GLDebug.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        constructors[0].setAccessible(true);
        assertInstanceOf(GLDebug.class, constructors[0].newInstance());
    }

    // Checks that a fresh class makes no driver calls before the first reload
    @Test
    void freshClassStaysOffBeforeTheFirstReload() throws ReflectiveOperationException {
        Class<?> fresh = new FreshClasses(GLDebug.class).initialise(GLDebug.class);
        fresh.getMethod("nameObject", int.class, int.class, String.class).invoke(null, GL_BUFFER, 1, "early");
        fresh.getMethod("pushGroup", int.class, String.class).invoke(null, 1, "early");
        fresh.getMethod("popGroup").invoke(null);
        verifyNoInteractions(gl());
    }

    // Checks that an unset property leaves debugging off without touching the context
    @Test
    void unsetPropertyLeavesDebuggingOffWithoutTouchingTheContext() {
        GLDebug.reloadDebugState();
        GLDebug.nameObject(GL_BUFFER, 1, "terrain");
        GLDebug.pushGroup(1, "terrain");
        GLDebug.popGroup();
        verifyNoInteractions(gl());
    }

    // Checks that only a case insensitive true turns debugging on
    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True"})
    void trueInAnyCaseTurnsDebuggingOn(String value) {
        System.setProperty(PROPERTY, value);
        GLDebug.reloadDebugState();
        verify(gl()).glGetInteger(GL_MAX_LABEL_LENGTH);
    }

    // Checks that any other property value leaves debugging off without touching the context
    @ParameterizedTest
    @ValueSource(strings = {"false", "", "1", "yes", " true", "true "})
    void otherValuesLeaveDebuggingOff(String value) {
        System.setProperty(PROPERTY, value);
        GLDebug.reloadDebugState();
        GLDebug.nameObject(GL_BUFFER, 1, "terrain");
        GLDebug.pushGroup(1, "terrain");
        GLDebug.popGroup();
        verifyNoInteractions(gl());
    }

    // Checks that a context without GL 4.3 or KHR_debug leaves debugging off and reads no limits
    @Test
    void contextWithoutDebugSupportLeavesDebuggingOff() {
        System.setProperty(PROPERTY, "true");
        when(gl().isOpenGLVersionSupported(4, 3)).thenReturn(false);
        when(gl().isExtensionSupported(GLExtension.KHR_debug)).thenReturn(false);
        GLDebug.reloadDebugState();
        GLDebug.nameObject(GL_BUFFER, 1, "terrain");
        GLDebug.pushGroup(1, "terrain");
        GLDebug.popGroup();
        verify(gl(), never()).glGetInteger(anyInt());
        verify(gl(), never()).glObjectLabel(anyInt(), anyInt(), any());
        verify(gl(), never()).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), never()).glPopDebugGroup();
    }

    // Checks that GL 4.3 turns debugging on without asking for the extension
    @Test
    void openGl43SkipsTheExtensionQuery() {
        System.setProperty(PROPERTY, "true");
        when(gl().glGetInteger(GL_MAX_LABEL_LENGTH)).thenReturn(LABEL_LENGTH);
        GLDebug.reloadDebugState();
        verify(gl(), never()).isExtensionSupported(any());
        assertEquals("terrain", labelled("terrain"));
    }

    // Checks that KHR_debug alone turns debugging on
    @Test
    void khrDebugAloneTurnsDebuggingOn() {
        when(gl().isOpenGLVersionSupported(4, 3)).thenReturn(false);
        when(gl().isExtensionSupported(GLExtension.KHR_debug)).thenReturn(true);
        enable();
        assertEquals("terrain", labelled("terrain"));
        assertEquals("terrain", pushed("terrain"));
        verify(gl()).glPopDebugGroup();
    }

    // Checks that each reload reads all three driver limits once
    @Test
    void eachReloadReadsTheDriverLimitsOnce() {
        enable();
        GLDebug.reloadDebugState();
        GLDebug.reloadDebugState();
        verify(gl(), times(2)).glGetInteger(GL_MAX_LABEL_LENGTH);
        verify(gl(), times(2)).glGetInteger(GL_MAX_DEBUG_MESSAGE_LENGTH);
        verify(gl(), times(2)).glGetInteger(GL_MAX_DEBUG_GROUP_STACK_DEPTH);
    }

    // Checks that a reload forgets groups pushed before it
    @Test
    void reloadClearsTheGroupCount() {
        enable();
        GLDebug.pushGroup(1, "a");
        GLDebug.pushGroup(2, "b");
        GLDebug.reloadDebugState();
        GLDebug.popGroup();
        verify(gl(), never()).glPopDebugGroup();
    }

    // Checks that turning debugging off mid frame stops pushes and pops
    @Test
    void switchingOffMidFrameStopsPushesAndPops() {
        enable();
        GLDebug.pushGroup(1, "a");
        System.setProperty(PROPERTY, "false");
        GLDebug.reloadDebugState();
        GLDebug.popGroup();
        GLDebug.pushGroup(2, "b");
        GLDebug.popGroup();
        GLDebug.nameObject(GL_BUFFER, 1, "c");
        verify(gl(), times(1)).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), never()).glPopDebugGroup();
        verify(gl(), never()).glObjectLabel(anyInt(), anyInt(), any());
    }

    // Checks that a reload failing on the context check keeps the earlier state
    @Test
    void reloadFailingOnTheContextKeepsTheEarlierState() {
        enable();
        GLDebug.pushGroup(1, "a");
        when(gl().isOpenGLVersionSupported(4, 3)).thenThrow(new IllegalStateException("no context"));
        assertThrows(IllegalStateException.class, GLDebug::reloadDebugState);
        assertEquals("b", labelled("b"));
        GLDebug.popGroup();
        verify(gl()).glPopDebugGroup();
    }

    // Checks that a reload failing on a limit query keeps the earlier state
    @Test
    void reloadFailingOnALimitKeepsTheEarlierState() {
        enable(8, MESSAGE_LENGTH, STACK_DEPTH);
        GLDebug.pushGroup(1, "a");
        when(gl().glGetInteger(GL_MAX_DEBUG_GROUP_STACK_DEPTH)).thenThrow(new IllegalStateException("lost context"));
        when(gl().glGetInteger(GL_MAX_LABEL_LENGTH)).thenReturn(LABEL_LENGTH);
        assertThrows(IllegalStateException.class, GLDebug::reloadDebugState);
        assertEquals("abcdefg", labelled("abcdefghij"));
        GLDebug.popGroup();
        verify(gl()).glPopDebugGroup();
    }

    // Checks that a null name is rejected whether debugging is on or off
    @Test
    void nameObjectRejectsANullName() {
        assertThrows(NullPointerException.class, () -> GLDebug.nameObject(GL_BUFFER, 1, null));
        enable();
        assertThrows(NullPointerException.class, () -> GLDebug.nameObject(GL_BUFFER, 1, null));
        verify(gl(), never()).glObjectLabel(anyInt(), anyInt(), any());
    }

    // Checks that identifiers and object names reach the driver unchanged
    @Test
    void nameObjectPassesIdentifierAndObjectThrough() {
        enable();
        for (int identifier : EXTREMES) {
            for (int object : EXTREMES) {
                GLDebug.nameObject(identifier, object, "x");
                verify(gl()).glObjectLabel(identifier, object, "x");
            }
        }
    }

    // Checks that a fitting name reaches the driver as the same instance
    @Test
    void fittingNameIsPassedAsTheSameInstance() {
        enable();
        String name = new String("terrain pass");
        assertSame(name, labelled(name));
        String full = "a".repeat(LABEL_LENGTH - 1);
        assertSame(full, labelled(full));
    }

    // Checks that an empty name reaches the driver empty
    @Test
    void emptyNameIsPassedThrough() {
        enable();
        assertEquals("", labelled(""));
    }

    // Checks that a name one byte over the limit loses its last char
    @Test
    void nameOneByteOverTheLimitIsCut() {
        enable();
        assertEquals("a".repeat(LABEL_LENGTH - 1), labelled("a".repeat(LABEL_LENGTH)));
    }

    // Checks that a single char name is cut to empty at a one byte limit and kept at two
    @Test
    void singleCharNameAtTheSmallestLimits() {
        enable(1, MESSAGE_LENGTH, STACK_DEPTH);
        assertEquals("", labelled("a"));
        assertEquals("", labelled(""));
        enable(2, MESSAGE_LENGTH, STACK_DEPTH);
        assertEquals("a", labelled("a"));
        assertEquals("a", labelled("ab"));
    }

    // Checks the cut for two and three and four byte encodings
    @ParameterizedTest
    @ValueSource(strings = {"\u00e9", "\u20ac", "\ud83d\ude00", "a\u00e9\u20ac\ud83d\ude00"})
    void multiByteNamesAreCutOnCodePoints(String unit) {
        enable();
        String name = unit.repeat(300);
        assertLongestFit(name, labelled(name), LABEL_LENGTH - 1);
    }

    // Checks that a surrogate pair ending on the limit is kept and one byte later is dropped whole
    @Test
    void surrogatePairOnTheLimitBoundary() {
        enable();
        String exact = "a".repeat(LABEL_LENGTH - 5) + "\ud83d\ude00";
        assertSame(exact, labelled(exact));
        String over = "a".repeat(LABEL_LENGTH - 4) + "\ud83d\ude00";
        assertEquals("a".repeat(LABEL_LENGTH - 4), labelled(over));
    }

    // Checks that lone surrogates count as the one byte the encoder writes
    @ParameterizedTest
    @ValueSource(strings = {"\ud800", "\udc00", "\ud800a", "a\ud800"})
    void loneSurrogatesCountAsOneByte(String unit) {
        enable(8, MESSAGE_LENGTH, STACK_DEPTH);
        String name = unit.repeat(10);
        String cut = labelled(name);
        assertEquals(7, cut.length());
        assertEquals(7, utf8(cut));
        assertLongestFit(name, cut, 7);
    }

    // Checks that repeated reversed surrogates count the lone ends as one byte and the inner pairs as four
    @Test
    void reversedSurrogatesPairUpAtTheInnerJoins() {
        enable(8, MESSAGE_LENGTH, STACK_DEPTH);
        String name = "\udc00\ud800".repeat(10);
        String cut = labelled(name);
        assertEquals("\udc00\ud800\udc00", cut);
        assertEquals(5, utf8(cut));
        assertLongestFit(name, cut, 7);
    }

    // Checks that a name ending in a high surrogate within the limit is kept
    @Test
    void trailingHighSurrogateWithinTheLimitIsKept() {
        enable(8, MESSAGE_LENGTH, STACK_DEPTH);
        String name = "abcdef\ud800";
        assertSame(name, labelled(name));
    }

    // Checks that a name larger than the native stack reaches the driver cut to the limit
    @Test
    void nameLargerThanTheNativeStackIsCut() {
        enable();
        String name = "terrain \u00e9\u20ac\ud83d\ude00 ".repeat(20_000);
        String cut = labelled(name);
        assertLongestFit(name, cut, LABEL_LENGTH - 1);
    }

    // Checks that a zero or negative label limit stops labels and leaves groups working
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonPositiveLabelLimitStopsLabels(int labelLength) {
        enable(labelLength, MESSAGE_LENGTH, STACK_DEPTH);
        GLDebug.nameObject(GL_BUFFER, 1, "terrain");
        verify(gl(), never()).glObjectLabel(anyInt(), anyInt(), any());
        assertEquals("terrain", pushed("terrain"));
    }

    // Checks that the largest label limit passes a long name whole
    @Test
    void largestLabelLimitPassesALongNameWhole() {
        enable(Integer.MAX_VALUE, MESSAGE_LENGTH, STACK_DEPTH);
        String name = "x".repeat(100_000);
        assertSame(name, labelled(name));
    }

    // Checks that repeated labels each reach the driver
    @Test
    void repeatedLabelsEachReachTheDriver() {
        enable();
        GLDebug.nameObject(GL_PROGRAM, 3, "terrain");
        GLDebug.nameObject(GL_PROGRAM, 3, "terrain");
        verify(gl(), times(2)).glObjectLabel(GL_PROGRAM, 3, "terrain");
    }

    // Checks that a null group name is rejected while off and on and past the depth limit
    @Test
    void pushGroupRejectsANullName() {
        assertThrows(NullPointerException.class, () -> GLDebug.pushGroup(1, null));
        enable(LABEL_LENGTH, MESSAGE_LENGTH, 3);
        assertThrows(NullPointerException.class, () -> GLDebug.pushGroup(1, null));
        GLDebug.pushGroup(2, "kept");
        assertThrows(NullPointerException.class, () -> GLDebug.pushGroup(3, null));
        GLDebug.popGroup();
        GLDebug.popGroup();
        verify(gl(), times(1)).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), times(1)).glPopDebugGroup();
    }

    // Checks that group ids reach the driver unchanged with the application source
    @Test
    void pushGroupPassesIdsThrough() {
        enable();
        for (int id : EXTREMES) {
            GLDebug.pushGroup(id, "g");
            verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, id, "g");
            GLDebug.popGroup();
        }
        verify(gl(), times(EXTREMES.length)).glPopDebugGroup();
    }

    // Checks that a fitting group name reaches the driver as the same instance
    @Test
    void fittingGroupNameIsPassedAsTheSameInstance() {
        enable();
        String name = new String("SOLID terrain pass");
        assertSame(name, pushed(name));
    }

    // Checks that a group name over the message limit is cut on code points
    @Test
    void groupNameOverTheMessageLimitIsCut() {
        enable(LABEL_LENGTH, 16, STACK_DEPTH);
        String name = "\u20ac".repeat(40);
        String cut = pushed(name);
        assertLongestFit(name, cut, 15);
        assertEquals(5, cut.length());
    }

    // Checks that a one byte message limit pushes empty messages
    @Test
    void oneByteMessageLimitPushesEmptyMessages() {
        enable(LABEL_LENGTH, 1, STACK_DEPTH);
        assertEquals("", pushed("terrain"));
        verify(gl()).glPopDebugGroup();
    }

    // Checks that a zero or negative message limit stops groups and keeps labels and balance
    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonPositiveMessageLimitStopsGroups(int messageLength) {
        enable(LABEL_LENGTH, messageLength, STACK_DEPTH);
        GLDebug.pushGroup(1, "a");
        GLDebug.pushGroup(2, "b");
        GLDebug.popGroup();
        GLDebug.popGroup();
        GLDebug.popGroup();
        verify(gl(), never()).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), never()).glPopDebugGroup();
        assertEquals("terrain", labelled("terrain"));
    }

    // Checks that pushes past the driver depth are counted and their pops skipped
    @Test
    void pushesPastTheDriverDepthAreCountedAndSkipped() {
        enable();
        for (int i = 0; i < GROUPS + 8; i++) {
            GLDebug.pushGroup(i, "g" + i);
        }
        verify(gl(), times(GROUPS)).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, GROUPS - 1, "g" + (GROUPS - 1));
        verify(gl(), never()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, GROUPS, "g" + GROUPS);
        for (int i = 0; i < 8; i++) {
            GLDebug.popGroup();
        }
        verify(gl(), never()).glPopDebugGroup();
        for (int i = 0; i < GROUPS + 1; i++) {
            GLDebug.popGroup();
        }
        verify(gl(), times(GROUPS)).glPopDebugGroup();
    }

    // Checks that a group popped below the depth limit makes room for the next push
    @Test
    void popBelowTheLimitMakesRoomForTheNextPush() {
        enable(LABEL_LENGTH, MESSAGE_LENGTH, 4);
        GLDebug.pushGroup(1, "a");
        GLDebug.pushGroup(2, "b");
        GLDebug.pushGroup(3, "c");
        GLDebug.popGroup();
        GLDebug.popGroup();
        GLDebug.pushGroup(4, "d");
        verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 1, "a");
        verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 2, "b");
        verify(gl(), never()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 3, "c");
        verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 4, "d");
        verify(gl(), times(1)).glPopDebugGroup();
    }

    // Checks that driver depths too small for a spare group push nothing and stay balanced
    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 2})
    void tinyDriverDepthsPushNothing(int stackDepth) {
        enable(LABEL_LENGTH, MESSAGE_LENGTH, stackDepth);
        GLDebug.pushGroup(1, "a");
        GLDebug.popGroup();
        GLDebug.popGroup();
        verify(gl(), never()).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), never()).glPopDebugGroup();
    }

    // Checks that a driver depth of three holds exactly one group
    @Test
    void driverDepthOfThreeHoldsOneGroup() {
        enable(LABEL_LENGTH, MESSAGE_LENGTH, 3);
        GLDebug.pushGroup(1, "a");
        GLDebug.pushGroup(2, "b");
        GLDebug.popGroup();
        GLDebug.popGroup();
        verify(gl(), times(1)).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 1, "a");
        verify(gl(), times(1)).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), times(1)).glPopDebugGroup();
    }

    // Checks that the largest driver depth pushes every group
    @Test
    void largestDriverDepthPushesEveryGroup() {
        enable(LABEL_LENGTH, MESSAGE_LENGTH, Integer.MAX_VALUE);
        for (int i = 0; i < 1000; i++) {
            GLDebug.pushGroup(i, "g");
        }
        for (int i = 0; i < 1001; i++) {
            GLDebug.popGroup();
        }
        verify(gl(), times(1000)).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), times(1000)).glPopDebugGroup();
    }

    // Checks that the group count runs past the int range without wrapping
    @Test
    void groupCountRunsPastTheIntRange() {
        enable(LABEL_LENGTH, MESSAGE_LENGTH, Integer.MAX_VALUE);
        Statics.set(GLDebug.class, "depth", (long) Integer.MAX_VALUE);
        GLDebug.pushGroup(1, "a");
        assertEquals(1L << 31, (long) Statics.get(GLDebug.class, "depth"));
        verify(gl(), never()).glPushDebugGroup(anyInt(), anyInt(), any());
        GLDebug.popGroup();
        GLDebug.popGroup();
        GLDebug.popGroup();
        verify(gl(), never()).glPopDebugGroup();
        Statics.set(GLDebug.class, "depth", (long) Integer.MAX_VALUE - 2);
        GLDebug.popGroup();
        verify(gl()).glPopDebugGroup();
        assertEquals((long) Integer.MAX_VALUE - 3, (long) Statics.get(GLDebug.class, "depth"));
    }

    // Checks that nested pushes and pops reach the driver in order
    @Test
    void nestedGroupsReachTheDriverInOrder() {
        enable();
        GLDebug.pushGroup(1, "a");
        GLDebug.pushGroup(2, "b");
        GLDebug.popGroup();
        GLDebug.pushGroup(3, "c");
        GLDebug.popGroup();
        GLDebug.popGroup();
        GLDebug.popGroup();
        InOrder order = inOrder(gl());
        order.verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 1, "a");
        order.verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 2, "b");
        order.verify(gl()).glPopDebugGroup();
        order.verify(gl()).glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 3, "c");
        order.verify(gl(), times(2)).glPopDebugGroup();
        order.verifyNoMoreInteractions();
    }

    // Checks that pops without a push never reach the driver
    @Test
    void popWithoutAPushIsIgnored() {
        enable();
        GLDebug.popGroup();
        GLDebug.popGroup();
        verify(gl(), never()).glPopDebugGroup();
    }

    // Checks that groups stay off while debugging is off
    @Test
    void groupsStayOffWhileDebuggingIsOff() {
        GLDebug.reloadDebugState();
        GLDebug.pushGroup(1, "a");
        GLDebug.popGroup();
        verify(gl(), never()).glPushDebugGroup(anyInt(), anyInt(), any());
        verify(gl(), never()).glPopDebugGroup();
    }

    // Checks the fit helper at empty and single char inputs and the smallest limits
    @Test
    void fitHandlesEmptyAndSingleCharInputs() {
        String empty = new String("");
        assertSame(empty, fit(empty, 0));
        assertEquals("", fit("a", 0));
        String one = new String("a");
        assertSame(one, fit(one, 1));
        assertEquals("", fit("\u00e9", 1));
        String two = new String("\u00e9");
        assertSame(two, fit(two, 2));
        assertEquals("", fit("\u20ac", 2));
        assertEquals("", fit("\ud83d\ude00", 3));
        String four = new String("\ud83d\ude00");
        assertSame(four, fit(four, 4));
    }

    // Checks the fit helper at the largest limit and on a prefix cut mid text
    @Test
    void fitHandlesTheLargestLimitAndMidTextCuts() {
        String text = new String("abc\u00e9\u20ac\ud83d\ude00");
        assertSame(text, fit(text, Integer.MAX_VALUE));
        assertSame(text, fit(text, 12));
        assertEquals("abc\u00e9\u20ac", fit(text, 11));
        assertEquals("abc\u00e9\u20ac", fit(text, 8));
        assertEquals("abc\u00e9", fit(text, 7));
        assertEquals("abc", fit(text, 4));
    }

    // Checks that fit agrees with the JDK encoder on every prefix length of a mixed text
    @Test
    void fitAgreesWithTheEncoderOnEveryLimit() {
        String text = "a\u00e9\u20ac\ud83d\ude00\ud800b\udc00\u07ff\u0800\uffff\ud83d";
        for (int limit = 0; limit <= utf8(text) + 1; limit++) {
            assertLongestFit(text, fit(text, limit), limit);
        }
    }

    // Checks that the fit helper is private and static
    @Test
    void fitIsPrivateAndStatic() throws NoSuchMethodException {
        Method fit = GLDebug.class.getDeclaredMethod("fit", String.class, int.class);
        assertTrue(Modifier.isPrivate(fit.getModifiers()));
        assertTrue(Modifier.isStatic(fit.getModifiers()));
    }
}
