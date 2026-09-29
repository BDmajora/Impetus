package com.bdmajora.coarctatio.client.texture;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShelfStitcherTest {
    // A sprite with nothing but a name and a size, which is all the packer looks at
    private static Stitcher.Holder holder(String name, int width, int height) {
        TextureAtlasSprite sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "iconName", name);
        Mixins.set(sprite, "width", width);
        Mixins.set(sprite, "height", height);
        return new Stitcher.Holder(sprite, 0);
    }

    @Test
    void anEmptyAtlasHasNoSize() {
        ShelfStitcher.Result result = ShelfStitcher.stitch(new Stitcher.Holder[0], 1024, 1024);
        assertEquals(0, result.width);
        assertEquals(0, result.height);
        assertTrue(result.placements.isEmpty());
        assertNotNull(Mixins.construct(ShelfStitcher.class));
    }

    @Test
    void everySpriteIsPlacedOnceInsideTheAtlas() {
        Stitcher.Holder[] holders = new Stitcher.Holder[64];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = holder("block" + i, 16, 16);
        }
        ShelfStitcher.Result result = ShelfStitcher.stitch(holders, 1024, 1024);
        assertEquals(64, result.placements.size());
        assertTrue(result.width > 0 && result.height > 0);

        Set<Long> occupied = new HashSet<>();
        for (ShelfStitcher.Placement placement : result.placements) {
            assertTrue(placement.x >= 0 && placement.x + placement.holder.getWidth() <= result.width);
            assertTrue(placement.y >= 0 && placement.y + placement.holder.getHeight() <= result.height);
            // Cells are claimed by one sprite each
            for (int x = placement.x; x < placement.x + placement.holder.getWidth(); x++) {
                for (int y = placement.y; y < placement.y + placement.holder.getHeight(); y++) {
                    assertTrue(occupied.add(((long) x << 32) | y), "overlap at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void theGapOverAShortSpriteIsRefilledByLaterOnes() {
        // A big sprite opens a row, a smaller one beside it leaves a gap over itself, and the small ones land in it
        Stitcher.Holder[] holders = {
                holder("big", 64, 64),
                holder("medium", 32, 32),
                holder("small_a", 16, 16),
                holder("small_b", 16, 16),
        };
        ShelfStitcher.Result result = ShelfStitcher.stitch(holders, 512, 512);
        assertEquals(4, result.placements.size());
        // Everything fits in the one row the big sprite opened, the small ones in the space above the medium one
        assertEquals(64, result.height);
        for (ShelfStitcher.Placement placement : result.placements) {
            assertTrue(placement.y + placement.holder.getHeight() <= 64);
        }
    }

    @Test
    void aTallLayoutIsRetriedWiderWhenThereIsRoom() {
        Stitcher.Holder[] holders = new Stitcher.Holder[16];
        for (int i = 0; i < holders.length; i++) {
            holders[i] = holder("tall" + i, 64, 64);
        }
        ShelfStitcher.Result result = ShelfStitcher.stitch(holders, 1024, 1024);
        // A square-ish atlas is preferred over a tall thin one
        assertTrue(result.height <= result.width, result.width + "x" + result.height);
    }

    @Test
    void aSpriteTooLargeForTheAtlasIsReported() {
        Stitcher.Holder[] holders = {holder("huge", 128, 128)};
        StitcherException thrown = assertThrows(StitcherException.class, () -> ShelfStitcher.stitch(holders, 64, 64));
        assertTrue(thrown.getMessage().contains("Unable to fit"));

        // Too tall rather than too wide is the other way to overflow the limit
        Stitcher.Holder[] many = new Stitcher.Holder[8];
        for (int i = 0; i < many.length; i++) {
            many[i] = holder("row" + i, 64, 64);
        }
        assertThrows(StitcherException.class, () -> ShelfStitcher.stitch(many, 64, 64));
    }
}
