package com.bdmajora.coarctatio.launch.discovery;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParserCodecTest {
    private static final Type MOD = Type.getType("Lnet/minecraftforge/fml/common/Mod;");

    // A scan result the way Forge's class visitor would have left it
    static ASMModParser parser(ModAnnotation... annotations) {
        ASMModParser parser = Mc.uninitialized(ASMModParser.class);
        Mixins.set(parser, "asmType", Type.getType("Lcom/example/ExampleMod;"));
        Mixins.set(parser, "classVersion", 52);
        Mixins.set(parser, "asmSuperType", Type.getType("Ljava/lang/Object;"));
        Mixins.set(parser, "interfaces", new java.util.HashSet<>(Arrays.asList("java/io/Serializable")));
        Mixins.set(parser, "annotations", new LinkedList<>(Arrays.asList(annotations)));
        return parser;
    }

    static ModAnnotation annotation(Map<String, Object> values) {
        try {
            Class<?> kind = Class.forName("net.minecraftforge.fml.common.discovery.asm.ASMModParser$AnnotationType");
            Constructor<?> ctor = ModAnnotation.class.getConstructor(kind, Type.class, String.class);
            ModAnnotation annotation = (ModAnnotation) ctor.newInstance(kind.getEnumConstants()[0], MOD, "modid");
            Mixins.set(annotation, "values", values);
            return annotation;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] encoded(ASMModParser parser) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            ParserCodec.encode(parser, out);
        }
        return bytes.toByteArray();
    }

    private static ASMModParser decoded(byte[] blob) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            return ParserCodec.decode(in);
        }
    }

    @Test
    void aScanResultSurvivesTheRoundTrip() throws Exception {
        assertTrue(ParserCodec.canDecode());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("nothing", null);
        values.put("string", "example");
        values.put("type", MOD);
        values.put("int", 7);
        values.put("long", 8L);
        values.put("boolean", true);
        values.put("float", 1.5F);
        values.put("double", 2.5D);
        values.put("byte", (byte) 3);
        values.put("short", (short) 4);
        values.put("char", 'x');
        values.put("enum", new ModAnnotation.EnumHolder("Lcom/example/Kind;", "FIRST"));
        values.put("list", Arrays.asList("a", 1));
        values.put("nested", new HashMap<>(java.util.Collections.singletonMap("inner", "value")));

        ASMModParser original = parser(annotation(values));
        ASMModParser restored = decoded(encoded(original));

        assertEquals(Type.getType("Lcom/example/ExampleMod;"), Mixins.get(restored, "asmType"));
        assertEquals(52, (int) Mixins.<Integer>get(restored, "classVersion"));
        assertEquals(Type.getType("Ljava/lang/Object;"), Mixins.get(restored, "asmSuperType"));
        assertEquals(Set.of("java/io/Serializable"), Mixins.get(restored, "interfaces"));

        LinkedList<ModAnnotation> annotations = Mixins.get(restored, "annotations");
        assertEquals(1, annotations.size());
        ModAnnotation annotation = annotations.getFirst();
        assertEquals(MOD, annotation.getASMType());
        assertEquals("modid", annotation.getMember());

        Map<String, Object> read = Mixins.get(annotation, "values");
        assertNull(read.get("nothing"));
        assertEquals("example", read.get("string"));
        assertEquals(MOD, read.get("type"));
        assertEquals(7, read.get("int"));
        assertEquals(8L, read.get("long"));
        assertEquals(true, read.get("boolean"));
        assertEquals(1.5F, read.get("float"));
        assertEquals(2.5D, read.get("double"));
        assertEquals((byte) 3, read.get("byte"));
        assertEquals((short) 4, read.get("short"));
        assertEquals('x', read.get("char"));
        assertEquals("FIRST", ((ModAnnotation.EnumHolder) read.get("enum")).getValue());
        assertEquals(Arrays.asList("a", 1), read.get("list"));
        assertEquals(java.util.Collections.singletonMap("inner", "value"), read.get("nested"));
    }

    @Test
    void aClassWithNothingToRecordEncodesToAlmostNothing() throws Exception {
        ASMModParser empty = Mc.uninitialized(ASMModParser.class);
        ASMModParser restored = decoded(encoded(empty));
        // Null types and absent collections come back as nulls and empties rather than failing the decode
        assertNull(Mixins.get(restored, "asmType"));
        assertTrue(Mixins.<Set<String>>get(restored, "interfaces").isEmpty());
        assertTrue(Mixins.<LinkedList<ModAnnotation>>get(restored, "annotations").isEmpty());
        assertTrue(ParserCodec.canEncode());
        assertNotNull(Mixins.construct(ParserCodec.class));
    }

    @Test
    void anAnnotationValueTheCodecDoesNotKnowIsRefused() throws Exception {
        ASMModParser parser = parser(annotation(java.util.Collections.singletonMap("odd", new Object())));
        IOException thrown = assertThrows(IOException.class, () -> encoded(parser));
        assertTrue(thrown.getMessage().startsWith("Unencodable annotation value"));

        // And a blob with a tag from a future format is refused on the way back in
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            ParserCodec.encode(parser(annotation(java.util.Collections.singletonMap("string", "value"))), out);
        }
        byte[] blob = bytes.toByteArray();
        blob[blob.length - 8] = 99;
        assertThrows(IOException.class, () -> decoded(blob));
    }
}
