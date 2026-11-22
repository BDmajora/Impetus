package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.extras.mixin.misc.EntityDataManagerTypeGuardMixin;
import com.bdmajora.extras.mixin.misc.FramebufferBlitBlendMixin;
import com.bdmajora.impetus.umbra.mixin.compat.UmbraRenderingPipelineBlendGuardMixin;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.network.datasync.EntityDataManager;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.bdmajora.testing.MixinTargets.ats;
import static com.bdmajora.testing.MixinTargets.calls;
import static com.bdmajora.testing.MixinTargets.classNode;
import static com.bdmajora.testing.MixinTargets.find;
import static com.bdmajora.testing.MixinTargets.handler;
import static com.bdmajora.testing.MixinTargets.method;
import static com.bdmajora.testing.MixinTargets.previous;
import static com.bdmajora.testing.MixinTargets.resolve;
import static com.bdmajora.testing.MixinTargets.value;

// Resolves the sky, blit and data guard hooks against the real game bytecode, the way Mixin would (slice from/to, INVOKE/FIELD
// targets, ordinal), so a hook that lands on the wrong call fails here instead of in someone's game.
class SkyAndBlitTargetsTest {
    private static final String RENDER_GLOBAL = "net/minecraft/client/renderer/RenderGlobal";
    private static final String VERTEX_BUFFER = "net/minecraft/client/renderer/vertex/VertexBuffer";
    private static final String STATE = "net/minecraft/client/renderer/GlStateManager";

    @Test
    void theStarVboHookWrapsOnlyTheStarDrawNotTheSkyOrTheBlackVoidPlane() {
        MethodNode sky = method(RenderGlobal.class, "renderSky", "(FI)V");
        List<AbstractInsnNode> hits = resolve(sky, handler(RenderGlobalMixin.class, "impetus$drawStarVbo"));
        assertEquals(1, hits.size(), "star VBO hook must match exactly one draw");
        // The receiver of that draw is starVBO: the field read three calls back in the same block
        assertEquals("starVBO", receiverField(hits.get(0)));
        // Vanilla has three VBO draws in renderSky (sky, stars, the black plane under the horizon) and only the middle one is ours
        List<AbstractInsnNode> draws = calls(sky, VERTEX_BUFFER, "drawArrays");
        assertEquals(3, draws.size());
        assertEquals(List.of("skyVBO", "starVBO", "sky2VBO"), draws.stream().map(SkyAndBlitTargetsTest::receiverField).toList());
        assertTrue(draws.indexOf(hits.get(0)) == 1);
    }

    @Test
    void theStarListHookWrapsOnlyTheStarDisplayList() {
        MethodNode sky = method(RenderGlobal.class, "renderSky", "(FI)V");
        List<AbstractInsnNode> hits = resolve(sky, handler(RenderGlobalMixin.class, "impetus$drawStarList"));
        assertEquals(1, hits.size());
        assertEquals("starGLCallList", fieldRightBefore(hits.get(0)));
    }

    @Test
    void theLowerSkyHookIsTheFinalTintedDiscNotTheBlackPlaneUnderTheHorizon() {
        MethodNode sky = method(RenderGlobal.class, "renderSky", "(FI)V");
        List<AbstractInsnNode> hits = resolve(sky, handler(RenderGlobalMixin.class, "impetus$drawLowerSky"));
        assertEquals(1, hits.size());
        assertEquals("glSkyList2", fieldRightBefore(hits.get(0)));
        // Every callList of renderSky in order: sky list, star list, the black plane's list (below the horizon), the tinted disc
        List<AbstractInsnNode> lists = calls(sky, STATE, "callList");
        assertEquals(List.of("glSkyList", "starGLCallList", "glSkyList2", "glSkyList2"),
                lists.stream().map(SkyAndBlitTargetsTest::fieldRightBefore).toList());
        assertEquals(3, lists.indexOf(hits.get(0)), "the black plane drawn when the eyes are under getHorizon() must stay vanilla");
        // The disc's colour is set between isSkyColored and the call, so the hook draws with the tint, not with the black of the void
        int skyColored = sky.instructions.indexOf(calls(sky, "net/minecraft/world/WorldProvider", "isSkyColored").get(0));
        int disc = sky.instructions.indexOf(hits.get(0));
        long colours = calls(sky, STATE, "color").stream().map(sky.instructions::indexOf).filter(i -> i > skyColored && i < disc).count();
        assertEquals(2, colours, "one colour per isSkyColored branch");
    }

    @Test
    void vanillaDrawsAPureBlackPlaneUnderTheHorizonWhenTheEyesAreBelowIt() {
        // Documents the vanilla source of a black horizon: color(0,0,0) then sky2 drawn only when eyeY - getHorizon() < 0
        MethodNode sky = method(RenderGlobal.class, "renderSky", "(FI)V");
        AbstractInsnNode horizon = calls(sky, "net/minecraft/client/multiplayer/WorldClient", "getHorizon").get(0);
        AbstractInsnNode branch = horizon;
        while (!(branch instanceof JumpInsnNode)) {
            branch = branch.getNext();
        }
        assertEquals(Opcodes.IFGE, branch.getOpcode(), "the plane is skipped when the eyes are at or above the horizon");
        AbstractInsnNode black = horizon;
        while (!(black instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals("color"))) {
            black = black.getPrevious();
        }
        assertEquals(List.of(Opcodes.FCONST_0, Opcodes.FCONST_0, Opcodes.FCONST_0), previousOpcodes(black, 3));
    }

    @Test
    void theBlitHookRunsRightAfterVanillasOnlyDisableBlendInsideItsFlagBranch() {
        MethodNode render = method(Framebuffer.class, "framebufferRenderExt", "(IIZ)V");
        List<AbstractInsnNode> hits = resolve(render, handler(FramebufferBlitBlendMixin.class, "impetus$forceBlendOff"));
        assertEquals(1, hits.size());
        // Guarded by the third argument: ILOAD 3, IFEQ right before the call
        AbstractInsnNode jump = previous(hits.get(0));
        assertEquals(Opcodes.IFEQ, jump.getOpcode());
        assertTrue(previous(jump) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ILOAD && load.var == 3);
        // Shifted AFTER, so the cache already reads "off" when the raw disable runs
        assertEquals("AFTER", value(at(FramebufferBlitBlendMixin.class, "impetus$forceBlendOff"), "shift"));
    }

    @Test
    void theDataGuardTargetsAndShadowsExistWithTheExpectedShapes() {
        ClassNode manager = classNode(EntityDataManager.class);
        assertNotNull(find(manager, "set", "(Lnet/minecraft/network/datasync/DataParameter;Ljava/lang/Object;)V"));
        assertNotNull(find(manager, "setEntryValues", "(Ljava/util/List;)V"));
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        for (FieldNode field : manager.fields) {
            fields.put(field.name, field.desc);
        }
        assertEquals("Ljava/util/Map;", fields.get("entries"));
        assertEquals("Lnet/minecraft/entity/Entity;", fields.get("entity"));
        // The guard's @At targets are HEAD, which always resolve; check its handlers are the ones the config would weave
        ClassNode guard = classNode(EntityDataManagerTypeGuardMixin.class);
        assertNotNull(find(guard, "impetus$refuseMismatchedSet", null));
        assertNotNull(find(guard, "impetus$dropMismatchedEntries", "(Ljava/util/List;)Ljava/util/List;"));
    }

    @Test
    void theBlendGuardReleasesExistOnThePipeline() {
        ClassNode pipeline = classNode(UmbraRenderingPipeline.class);
        assertNotNull(find(pipeline, "destroy", "()V"));
        assertNotNull(find(pipeline, "finishWorldRendering", "()V"));
        assertEquals("RETURN", value(at(UmbraRenderingPipelineBlendGuardMixin.class, "impetus$releaseAfterWorldFinish"), "value"));
        assertEquals("HEAD", value(at(UmbraRenderingPipelineBlendGuardMixin.class, "impetus$releaseAtDestroy"), "value"));
    }

    private static String receiverField(AbstractInsnNode call) {
        // VertexBuffer.drawArrays(I): the receiver field sits before the mode push
        for (AbstractInsnNode insn = call.getPrevious(); insn != null; insn = insn.getPrevious()) {
            if (insn instanceof FieldInsnNode field && field.desc.equals("L" + VERTEX_BUFFER + ";")) {
                return field.name;
            }
        }
        return null;
    }

    private static String fieldRightBefore(AbstractInsnNode call) {
        return previous(call) instanceof FieldInsnNode field ? field.name : null;
    }

    private static List<Integer> previousOpcodes(AbstractInsnNode insn, int count) {
        List<Integer> out = new ArrayList<>();
        for (AbstractInsnNode at = insn.getPrevious(); at != null && out.size() < count; at = at.getPrevious()) {
            if (at.getOpcode() >= 0) {
                out.add(0, at.getOpcode());
            }
        }
        return out;
    }

    // The single @At of a handler.
    private static AnnotationNode at(Class<?> mixin, String handler) {
        return ats(handler(mixin, handler)).get(0);
    }
}
