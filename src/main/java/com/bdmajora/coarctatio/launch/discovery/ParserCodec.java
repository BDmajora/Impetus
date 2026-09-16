package com.bdmajora.coarctatio.launch.discovery;

import com.bdmajora.coarctatio.mixin.forge.ASMModParserAccessor;
import com.bdmajora.coarctatio.mixin.forge.ModAnnotationAccessor;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.objectweb.asm.Type;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Writes and reads the result of one class scan: the class's type, version, super type, interfaces and every annotation with its values. Values are the kinds ASM's annotation visitor produces (strings, types, boxed primitives, enum holders, arrays as lists, nested annotations as their value maps); anything else fails the encode and that class is simply not cached
public final class ParserCodec {
    private static final byte T_NULL = 0;
    private static final byte T_STRING = 1;
    private static final byte T_TYPE = 2;
    private static final byte T_INT = 3;
    private static final byte T_LONG = 4;
    private static final byte T_BOOLEAN = 5;
    private static final byte T_FLOAT = 6;
    private static final byte T_DOUBLE = 7;
    private static final byte T_ENUM = 8;
    private static final byte T_LIST = 9;
    private static final byte T_MAP = 10;
    private static final byte T_BYTE = 11;
    private static final byte T_SHORT = 12;
    private static final byte T_CHAR = 13;

    // ASMModParser.AnnotationType is package-private, so ModAnnotation's constructor and the enum's constants are reached reflectively
    private static final Object[] ANNOTATION_TYPES;
    private static final java.lang.reflect.Constructor<?> ANNOTATION_CTOR;

    static {
        Object[] constants = null;
        java.lang.reflect.Constructor<?> ctor = null;
        try {
            Class<?> kind = Class.forName("net.minecraftforge.fml.common.discovery.asm.ASMModParser$AnnotationType");
            constants = kind.getEnumConstants();
            ctor = ModAnnotation.class.getConstructor(kind, Type.class, String.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Decoding is then refused; encoding still works since it only reads
        }
        ANNOTATION_TYPES = constants;
        ANNOTATION_CTOR = ctor;
    }

    // sun.misc.Unsafe resolved reflectively since the Java 8 API surface the build targets hides it; allocateInstance skips the constructor, which would want a class stream to parse
    private static final Object UNSAFE;
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
            // No Unsafe means no cached parsers can be built; the cache then only records, never serves
        }
        UNSAFE = unsafe;
        ALLOCATE_INSTANCE = allocate;
    }

    private ParserCodec() {
    }

    public static boolean canDecode() {
        return UNSAFE != null && ALLOCATE_INSTANCE != null && ANNOTATION_TYPES != null && ANNOTATION_CTOR != null;
    }

    public static void encode(ASMModParser parser, DataOutputStream out) throws IOException {
        ASMModParserAccessor fields = (ASMModParserAccessor) parser;
        writeType(out, fields.coarctatio$asmType());
        out.writeInt(fields.coarctatio$classVersion());
        writeType(out, fields.coarctatio$asmSuperType());
        Set<String> interfaces = fields.coarctatio$interfaces();
        out.writeInt(interfaces == null ? 0 : interfaces.size());
        if (interfaces != null) {
            for (String name : interfaces) {
                out.writeUTF(name);
            }
        }
        LinkedList<ModAnnotation> annotations = fields.coarctatio$annotations();
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

    public static ASMModParser decode(DataInputStream in) throws IOException {
        ASMModParser parser;
        try {
            parser = (ASMModParser) ALLOCATE_INSTANCE.invoke(UNSAFE, ASMModParser.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException(e);
        }
        ASMModParserAccessor fields = (ASMModParserAccessor) parser;
        fields.coarctatio$setAsmType(readType(in));
        fields.coarctatio$setClassVersion(in.readInt());
        fields.coarctatio$setAsmSuperType(readType(in));
        int interfaceCount = in.readInt();
        Set<String> interfaces = new HashSet<>(Math.max(4, interfaceCount * 2));
        for (int i = 0; i < interfaceCount; i++) {
            interfaces.add(in.readUTF());
        }
        fields.coarctatio$setInterfaces(interfaces);
        int annotationCount = in.readInt();
        LinkedList<ModAnnotation> annotations = new LinkedList<>();
        for (int i = 0; i < annotationCount; i++) {
            Object kind = ANNOTATION_TYPES[in.readByte()];
            Type type = readType(in);
            String member = readNullableUTF(in);
            ModAnnotation annotation;
            try {
                annotation = (ModAnnotation) ANNOTATION_CTOR.newInstance(kind, type, member);
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IOException(e);
            }
            ((ModAnnotationAccessor) annotation).coarctatio$setValues(readMap(in));
            annotations.add(annotation);
        }
        fields.coarctatio$setAnnotations(annotations);
        return parser;
    }

    private static void writeType(DataOutputStream out, Type type) throws IOException {
        writeNullableUTF(out, type == null ? null : type.getDescriptor());
    }

    private static Type readType(DataInputStream in) throws IOException {
        String descriptor = readNullableUTF(in);
        return descriptor == null ? null : Type.getType(descriptor);
    }

    private static void writeNullableUTF(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableUTF(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeMap(DataOutputStream out, Map<String, Object> map) throws IOException {
        out.writeInt(map == null ? 0 : map.size());
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                out.writeUTF(entry.getKey());
                writeValue(out, entry.getValue());
            }
        }
    }

    private static Map<String, Object> readMap(DataInputStream in) throws IOException {
        int size = in.readInt();
        Map<String, Object> map = new HashMap<>(Math.max(4, size * 2));
        for (int i = 0; i < size; i++) {
            String key = in.readUTF();
            map.put(key, readValue(in));
        }
        return map;
    }

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

    private static Object readValue(DataInputStream in) throws IOException {
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
                int size = in.readInt();
                ArrayList<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(readValue(in));
                }
                return list;
            }
            case T_MAP:
                return readMap(in);
            default:
                throw new IOException("Unknown annotation value tag " + tag);
        }
    }
}
