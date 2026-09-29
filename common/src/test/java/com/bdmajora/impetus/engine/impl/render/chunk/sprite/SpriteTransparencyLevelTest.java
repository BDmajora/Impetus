package com.bdmajora.impetus.engine.impl.render.chunk.sprite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpriteTransparencyLevelTest {
    @Test
    void theMoreCapablePassWins() {
        assertEquals(SpriteTransparencyLevel.TRANSLUCENT, SpriteTransparencyLevel.OPAQUE.chooseNextLevel(SpriteTransparencyLevel.TRANSLUCENT));
        assertEquals(SpriteTransparencyLevel.TRANSLUCENT, SpriteTransparencyLevel.TRANSLUCENT.chooseNextLevel(SpriteTransparencyLevel.OPAQUE));
        assertEquals(SpriteTransparencyLevel.TRANSPARENT, SpriteTransparencyLevel.TRANSPARENT.chooseNextLevel(SpriteTransparencyLevel.TRANSPARENT));
        SpriteTransparencyLevel.Holder holder = () -> SpriteTransparencyLevel.TRANSPARENT;
        assertEquals(SpriteTransparencyLevel.TRANSPARENT, SpriteTransparencyLevel.Holder.getTransparencyLevel(holder));
    }
}
