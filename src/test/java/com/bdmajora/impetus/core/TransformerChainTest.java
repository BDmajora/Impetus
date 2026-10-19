package com.bdmajora.impetus.core;

import com.bdmajora.testing.Mixins;
import net.minecraft.launchwrapper.IClassTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.outlands.foundation.boot.TransformerHolder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TransformerChainTest {
    private final IClassTransformer first = (name, transformedName, bytes) -> bytes;
    private final IClassTransformer second = (name, transformedName, bytes) -> bytes;
    private final IClassTransformer late = (name, transformedName, bytes) -> bytes;
    private List<IClassTransformer> previous;

    // Stands in for Cleanroom's TransformerFields, which the dev runtime's Cleanroom does not ship
    static final class Mirror {
        static List<IClassTransformer> transformers;
    }

    @BeforeEach
    void installLinkedChain() {
        this.previous = TransformerHolder.transformers;
        TransformerHolder.transformers = new LinkedList<>(List.of(this.first, this.second));
        Mirror.transformers = null;
    }

    @AfterEach
    void restoreChain() {
        TransformerHolder.transformers = this.previous;
        Mirror.transformers = null;
    }

    @Test
    void theChainBecomesCopyOnWriteInTheSameOrderAndOnlyOnce() {
        TransformerChain.makeConcurrent();
        List<IClassTransformer> chain = TransformerHolder.transformers;
        // Exactly the JDK class, from the bootstrap loader: anything the game's loader defines would load through the very walk it serves
        assertSame(CopyOnWriteArrayList.class, chain.getClass());
        assertNull(chain.getClass().getClassLoader());
        assertEquals(List.of(this.first, this.second), chain);
        TransformerChain.makeConcurrent();
        assertSame(chain, TransformerHolder.transformers);
    }

    @Test
    void aBootWithNoChainYetIsLeftAlone() {
        TransformerHolder.transformers = null;
        TransformerChain.makeConcurrent();
        assertNull(TransformerHolder.transformers);
    }

    @Test
    void aWalkSurvivesARegistrationFromAnotherThread() throws InterruptedException {
        TransformerChain.makeConcurrent();
        List<IClassTransformer> walked = new ArrayList<>();
        for (IClassTransformer transformer : TransformerHolder.transformers) {
            // What ModAPITransformer's registration did to the LinkedList mid-walk
            Thread register = new Thread(() -> TransformerHolder.transformers.add(this.late));
            register.start();
            register.join();
            walked.add(transformer);
        }
        assertEquals(List.of(this.first, this.second), walked);
        assertEquals(List.of(this.first, this.second, this.late, this.late), TransformerHolder.transformers);
    }

    @Test
    void foundationsOwnUnregisterStillPrunesTheChain() {
        TransformerChain.makeConcurrent();
        // TransformerDelegate.unRegisterTransformer streams the chain and removes from it as it goes, then re-sorts
        TransformerHolder.transformers.stream().filter(transformer -> transformer == this.first).forEach(TransformerHolder.transformers::remove);
        TransformerHolder.transformers.sort((a, b) -> 0);
        assertEquals(List.of(this.second), TransformerHolder.transformers);
    }

    @Test
    void cleanroomsMirrorFollowsOnlyWhenItHeldTheReplacedChain() {
        List<IClassTransformer> replaced = new LinkedList<>();
        List<IClassTransformer> replacement = new ArrayList<>();
        Mirror.transformers = replaced;
        TransformerChain.repointMirror(Mirror.class.getName(), replaced, replacement);
        assertSame(replacement, Mirror.transformers);

        // A mirror Cleanroom refilled itself is not ours to move
        List<IClassTransformer> own = new ArrayList<>();
        Mirror.transformers = own;
        TransformerChain.repointMirror(Mirror.class.getName(), replaced, replacement);
        assertSame(own, Mirror.transformers);

        // A Cleanroom without the mirror is skipped quietly
        assertDoesNotThrow(() -> TransformerChain.repointMirror("com.bdmajora.missing.Mirror", replaced, replacement));
    }

    @Test
    void theSwapLooksForCleanroomsOwnMirror() {
        assertNotNull(Mixins.construct(TransformerChain.class));
        assertEquals("com.cleanroommc.cleanroom.compatibility.CompatibilityHooks$TransformerFields", TransformerChain.CLEANROOM_MIRROR);
    }
}
