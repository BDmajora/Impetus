package com.bdmajora.impetus.engine.impl.render.chunk.sorting.trigger;

import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranslucencyTriggerIndexTest {
    @Test
    void reportsSectionsWhosePlanesTheCameraCrossed() {
        TranslucencyTriggerIndex index = new TranslucencyTriggerIndex();
        RenderSection section = new RenderSection(null, 0, 0, 0);
        RenderSection other = new RenderSection(null, 1, 0, 0);
        index.update(section, new NormalPlanes[]{new NormalPlanes(0, 1, 0, new float[]{4f, 8f})});
        index.update(other, new NormalPlanes[]{new NormalPlanes(1, 0, 0, new float[]{2f})});
        index.update(new RenderSection(null, 2, 0, 0), new NormalPlanes[0]);
        List<RenderSection> hit = new ArrayList<>();
        index.collectTriggered(0, 2, 0, 0, 6, 0, hit::add);
        assertEquals(List.of(section), hit);
        hit.clear();
        index.collectTriggered(0, 9, 0, 0, 12, 0, hit::add);
        assertTrue(hit.isEmpty());
        index.collectTriggered(0, 0, 0, 0, 3, 0, hit::add);
        assertTrue(hit.isEmpty());
        index.collectTriggered(0, 5, 5, 0, 5, 9, hit::add);
        assertTrue(hit.isEmpty());
        index.collectTriggered(17, 0, 0, 19, 0, 0, hit::add);
        assertEquals(List.of(other), hit);
        List<RenderSection> all = new ArrayList<>();
        index.forEachSection(all::add);
        assertEquals(2, all.size());
        index.remove(section);
        index.remove(section);
        hit.clear();
        index.collectTriggered(0, 2, 0, 0, 6, 0, hit::add);
        assertTrue(hit.isEmpty());
        index.clear();
        all.clear();
        index.forEachSection(all::add);
        assertTrue(all.isEmpty());
    }
}
