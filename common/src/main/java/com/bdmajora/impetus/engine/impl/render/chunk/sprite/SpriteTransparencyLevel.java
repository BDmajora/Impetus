package com.bdmajora.impetus.engine.impl.render.chunk.sprite;

// Kinds of transparency of a sprite.
public enum SpriteTransparencyLevel {
    // Sprite without transparent pixels.
    OPAQUE,
    // Sprite with fully transparent pixels.
    TRANSPARENT,
    // Sprite with partly transparent pixels.
    TRANSLUCENT;

    // Returns the level that needs the more capable pass.
    public SpriteTransparencyLevel chooseNextLevel(SpriteTransparencyLevel level) {
        return level.ordinal() >= this.ordinal() ? level : this;
    }

    // Object that knows its transparency level.
    public interface Holder {
        // Returns the transparency level.
        SpriteTransparencyLevel impetus$getTransparencyLevel();

        // Returns the transparency level of a sprite object.
        static SpriteTransparencyLevel getTransparencyLevel(Object o) {
            return ((Holder)o).impetus$getTransparencyLevel();
        }
    }
}
