package com.bdmajora.coarctatio.launch.discovery;

import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.objectweb.asm.Type;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Stores mod class scan results as bytes.
public final class ParserCodec {
    // Tag of a null value.
    private static final byte T_NULL = 0;
    // Tag of a string value.
    private static final byte T_STRING = 1;
    // Tag of a type value.
    private static final byte T_TYPE = 2;
    // Tag of an int value.
    private static final byte T_INT = 3;
    // Tag of a long value.
    private static final byte T_LONG = 4;
    // Tag of a boolean value.
    private static final byte T_BOOLEAN = 5;
    // Tag of a float value.
    private static final byte T_FLOAT = 6;
    // Tag of a double value.
    private static final byte T_DOUBLE = 7;
    // Tag of an enum value.
    private static final byte T_ENUM = 8;
    // Tag of a list value.
    private static final byte T_LIST = 9;
    // Tag of a map value.
    private static final byte T_MAP = 10;
    // Tag of a byte value.
    private static final byte T_BYTE = 11;
    // Tag of a short value.
    private static final byte T_SHORT = 12;
    // Tag of a char value.
    private static final byte T_CHAR = 13;
    // Largest accepted element count.
    private static final int MAX_COUNT = 1 << 20;
    // Largest accepted nesting of values.
    private static final int MAX_DEPTH = 32;

    // Annotation kinds of the parser.
    private static final Object[] ANNOTATION_TYPES;
    // Constructor of annotation objects.
    private static final java.lang.reflect.Constructor<?> ANNOTATION_CTOR;

    static {
        Object[] constants = null;
        java.lang.reflect.Constructor<?> ctor = null;
        try {
            Class<?> kind = Class.forName("net.minecraftforge.fml.common.discovery.asm.ASMModParser$AnnotationType");
            constants = kind.getEnumConstants();
            ctor = ModAnnotation.class.getConstructor(kind, Type.class, String.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        ANNOTATION_TYPES = constants;
        ANNOTATION_CTOR = ctor;
    }

    // Access to the type field of a parser.
    private static final VarHandle ASM_TYPE;
    // Access to the version field of a parser.
    private static final VarHandle CLASS_VERSION;
    // Access to the super type field of a parser.
    private static final VarHandle ASM_SUPER_TYPE;
    // Access to the annotation list of a parser.
    private static final VarHandle ANNOTATIONS;
    // Access to the interface set of a parser.
    private static final VarHandle INTERFACES;
    // Access to the value map of an annotation.
    private static final VarHandle VALUES;

    static {
        VarHandle asmType = null, classVersion = null, asmSuperType = null, annotations = null, interfaces = null, values = null;
        try {
            MethodHandles.Lookup parser = MethodHandles.privateLookupIn(ASMModParser.class, MethodHandles.lookup());
            asmType = parser.findVarHandle(ASMModParser.class, "asmType", Type.class);
            classVersion = parser.findVarHandle(ASMModParser.class, "classVersion", int.class);
            asmSuperType = parser.findVarHandle(ASMModParser.class, "asmSuperType", Type.class);
            annotations = parser.findVarHandle(ASMModParser.class, "annotations", LinkedList.class);
            interfaces = parser.findVarHandle(ASMModParser.class, "interfaces", Set.class);
            values = MethodHandles.privateLookupIn(ModAnnotation.class, MethodHandles.lookup()).findVarHandle(ModAnnotation.class, "values", Map.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        ASM_TYPE = asmType;
        CLASS_VERSION = classVersion;
        ASM_SUPER_TYPE = asmSuperType;
        ANNOTATIONS = annotations;
        INTERFACES = interfaces;
        VALUES = values;
    }

    // Unsafe instance used to create parsers without a constructor.
    private static final Object UNSAFE;
    // Method that creates an object without a constructor.
    private static final java.lang.reflect.Method ALLOCATE_INSTANCE;

    static {
        Object unsafe = null;
        java.lang.reflect.Method allocate = null;
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field field = type.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = field.get(null);
            allocate = type.getMethod("allocateInstance", Class.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        UNSAFE = unsafe;
        ALLOCATE_INSTANCE = allocate;
    }

    // Prevents instantiation
    private ParserCodec() {
    }

    // Returns whether the parser fields are reachable.
    public static boolean canEncode() {
        return VALUES != null;
    }

    // Returns whether parsers can be rebuilt.
    public static boolean canDecode() {
        return canEncode() && UNSAFE != null && ALLOCATE_INSTANCE != null && ANNOTATION_TYPES != null && ANNOTATION_CTOR != null;
    }

    // Writes the parser and rejects an unreachable parser or an unsupported value.
    @SuppressWarnings("unchecked")
    public static void encode(ASMModParser parser, DataOutputStream out) throws IOException {
        if (!canEncode()) {
            throw new IOException("ASMModParser fields are not reachable");
        }
        writeType(out, (Type) ASM_TYPE.get(parser));
        out.writeInt((int) CLASS_VERSION.get(parser));
        writeType(out, (Type) ASM_SUPER_TYPE.get(parser));
        Set<String> interfaces = (Set<String>) INTERFACES.get(parser);
        out.writeInt(interfaces == null ? 0 : interfaces.size());
        if (interfaces != null) {
            for (String name : interfaces) {
                out.writeUTF(name);
            }
        }
        LinkedList<ModAnnotation> annotations = (LinkedList<ModAnnotation>) ANNOTATIONS.get(parser);
        out.writeInt(annotations == null ? 0 : annotations.size());
        if (annotations != null) {
            for (ModAnnotation annotation : annotations) {
                out.writeByte(((Enum<?>) (Object) annotation.getType()).ordinal());
                writeType(out, annotation.getASMType());
                writeNullableUTF(out, annotation.getMember());
                writeMap(out, annotation.getValues());
            }
        }
    }

    // Returns the parser read from the stream and rejects implausible data.
    public static ASMModParser decode(DataInputStream in) throws IOException {
        ASMModParser parser;
        try {
            parser = (ASMModParser) ALLOCATE_INSTANCE.invoke(UNSAFE, ASMModParser.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException(e);
        }
        ASM_TYPE.set(parser, readType(in));
        CLASS_VERSION.set(parser, in.readInt());
        ASM_SUPER_TYPE.set(parser, readType(in));
        int interfaceCount = readCount(in);
        Set<String> interfaces = new HashSet<>(Math.max(4, Math.min(interfaceCount, 1024) * 2));
        for (int i = 0; i < interfaceCount; i++) {
            interfaces.add(in.readUTF());
        }
        INTERFACES.set(parser, interfaces);
        int annotationCount = readCount(in);
        LinkedList<ModAnnotation> annotations = new LinkedList<>();
        for (int i = 0; i < annotationCount; i++) {
            byte kindIndex = in.readByte();
            if (kindIndex < 0 || kindIndex >= ANNOTATION_TYPES.length) {
                throw new IOException("Unknown annotation kind " + kindIndex);
            }
            Object kind = ANNOTATION_TYPES[kindIndex];
            Type type = readType(in);
            String member = readNullableUTF(in);
            ModAnnotation annotation;
            try {
                annotation = (ModAnnotation) ANNOTATION_CTOR.newInstance(kind, type, member);
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IOException(e);
            }
            VALUES.set(annotation, readMap(in, 0));
            annotations.add(annotation);
        }
        ANNOTATIONS.set(parser, annotations);
        return parser;
    }

    // Writes a type or null.
    private static void writeType(DataOutputStream out, Type type) throws IOException {
        writeNullableUTF(out, type == null ? null : type.getDescriptor());
    }

    // Returns a type or null.
    private static Type readType(DataInputStream in) throws IOException {
        String descriptor = readNullableUTF(in);
        return descriptor == null ? null : Type.getType(descriptor);
    }

    // Writes a string or null.
    private static void writeNullableUTF(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    // Returns a string or null.
    private static String readNullableUTF(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    // Writes a map of annotation values.
    private static void writeMap(DataOutputStream out, Map<String, Object> map) throws IOException {
        out.writeInt(map == null ? 0 : map.size());
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                writeValue(out, entry.getValue());
            }
        }
    }

    // Returns an element count and rejects a negative or huge count.
    private static int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_COUNT) {
            throw new IOException("Implausible count " + count);
        }
        return count;
    }

    // Returns a map of annotation values and rejects deep nesting.
    private static Map<String, Object> readMap(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("Annotation values nest too deeply");
        }
        int size = readCount(in);
        Map<String, Object> map = new HashMap<>(Math.max(4, Math.min(size, 1024) * 2));
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            map.put(key, readValue(in, depth + 1));
        }
        return map;
    }

    // Writes one annotation value.
    @SuppressWarnings("unchecked")
    private static void writeValue(DataOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.writeByte(T_NULL);
        } else if (value instanceof String) {
            out.writeByte(T_STRING);
            out.writeUTF((String) value);
        } else if (value instanceof Type) {
            out.writeByte(T_TYPE);
            out.writeUTF(((Type) value).getDescriptor());
        } else if (value instanceof Integer) {
            out.writeByte(T_INT);
            out.writeInt((Integer) value);
        } else if (value instanceof Long) {
            out.writeByte(T_LONG);
            out.writeLong((Long) value);
        } else if (value instanceof Boolean) {
            out.writeByte(T_BOOLEAN);
            out.writeBoolean((Boolean) value);
        } else if (value instanceof Float) {
            out.writeByte(T_FLOAT);
            out.writeFloat((Float) value);
        } else if (value instanceof Double) {
            out.writeByte(T_DOUBLE);
            out.writeDouble((Double) value);
        } else if (value instanceof Byte) {
            out.writeByte(T_BYTE);
            out.writeByte((Byte) value);
        } else if (value instanceof Short) {
            out.writeByte(T_SHORT);
            out.writeShort((Short) value);
        } else if (value instanceof Character) {
            out.writeByte(T_CHAR);
            out.writeChar((Character) value);
        } else if (value instanceof ModAnnotation.EnumHolder) {
            out.writeByte(T_ENUM);
            out.writeUTF(((ModAnnotation.EnumHolder) value).getDesc());
            out.writeUTF(((ModAnnotation.EnumHolder) value).getValue());
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            out.writeByte(T_LIST);
            out.writeInt(list.size());
            for (Object element : list) {
                writeValue(out, element);
            }
        } else if (value instanceof Map) {
            out.writeByte(T_MAP);
            writeMap(out, (Map<String, Object>) value);
        } else {
            throw new IOException("Unencodable annotation value " + value.getClass().getName());
        }
    }

    // Returns one annotation value and rejects an unknown tag or deep nesting.
    private static Object readValue(DataInputStream in, int depth) throws IOException {
        byte tag = in.readByte();
        switch (tag) {
            case T_NULL:
                return null;
            case T_STRING:
                return in.readUTF();
            case T_TYPE:
                return Type.getType(in.readUTF());
            case T_INT:
                return in.readInt();
            case T_LONG:
                return in.readLong();
            case T_BOOLEAN:
                return in.readBoolean();
            case T_FLOAT:
                return in.readFloat();
            case T_DOUBLE:
                return in.readDouble();
            case T_BYTE:
                return in.readByte();
            case T_SHORT:
                return in.readShort();
            case T_CHAR:
                return in.readChar();
            case T_ENUM:
                return new ModAnnotation.EnumHolder(in.readUTF(), in.readUTF());
            case T_LIST: {
                if (depth > MAX_DEPTH) {
                    throw new IOException("Annotation values nest too deeply");
                }
                int size = readCount(in);
                ArrayList<Object> list = new ArrayList<>(Math.min(size, 1024));
                for (int i = 0; i < size; i++) {
                    list.add(readValue(in, depth + 1));
                }
                return list;
            }
            case T_MAP:
                return readMap(in, depth + 1);
            default:
                throw new IOException("Unknown annotation value tag " + tag);
        }
    }
}
