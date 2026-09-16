package com.bdmajora.coarctatio.mixin.forge;

import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.LinkedList;
import java.util.Set;

// The parser's five result fields, so a cached scan can be poured back into an instance the class visitor never ran on. remap = false, Forge class
@Mixin(value = ASMModParser.class, remap = false)
public interface ASMModParserAccessor {
    @Accessor("asmType")
    Type coarctatio$asmType();

    @Accessor("asmType")
    void coarctatio$setAsmType(Type type);

    @Accessor("classVersion")
    int coarctatio$classVersion();

    @Accessor("classVersion")
    void coarctatio$setClassVersion(int version);

    @Accessor("asmSuperType")
    Type coarctatio$asmSuperType();

    @Accessor("asmSuperType")
    void coarctatio$setAsmSuperType(Type type);

    @Accessor("annotations")
    LinkedList<ModAnnotation> coarctatio$annotations();

    @Accessor("annotations")
    void coarctatio$setAnnotations(LinkedList<ModAnnotation> annotations);

    @Accessor("interfaces")
    Set<String> coarctatio$interfaces();

    @Accessor("interfaces")
    void coarctatio$setInterfaces(Set<String> interfaces);
}
