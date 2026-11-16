package com.bdmajora.impetus.umbra;

import com.bdmajora.impetus.umbra.terrain.ModernPackTransformer;
import com.bdmajora.impetus.umbra.util.SafeInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeIntTest {
    @Test
    void digitStringsSaturateInsteadOfThrowing() {
        assertEquals(0, SafeInt.parse("0"));
        assertEquals(330, SafeInt.parse("330"));
        assertEquals(Integer.MAX_VALUE, SafeInt.parse("2147483647"));
        assertEquals(Integer.MAX_VALUE, SafeInt.parse("2147483648"));
        assertEquals(Integer.MAX_VALUE, SafeInt.parse("9".repeat(5000)));
        assertThrows(NumberFormatException.class, () -> SafeInt.parse(""));
        assertThrows(NumberFormatException.class, () -> SafeInt.parse(null));
        assertThrows(NumberFormatException.class, () -> SafeInt.parse("-1"));
        assertThrows(NumberFormatException.class, () -> SafeInt.parse("1a"));
    }

    @Test
    void absurdVersionNumbersDoNotCrashThePackTransformer() {
        assertTrue(ModernPackTransformer.isModernSource("#version 99999999999999999999 core\nvoid main(){}"));
        assertNotNull(ModernPackTransformer.transform("#version 99999999999999999999 core\nvoid main(){}"));
    }
}
