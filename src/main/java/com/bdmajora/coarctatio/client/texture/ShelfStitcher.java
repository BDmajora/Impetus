package com.bdmajora.coarctatio.client.texture;

import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Packs sprites into rows with a first fit search of the gaps.
public final class ShelfStitcher {
    // Prevents instantiation
    private ShelfStitcher() {
    }

    // Position of one sprite.
    public static final class Placement {
        // Sprite that was placed.
        public final Stitcher.Holder holder;
        // Left edge of the sprite.
        public final int x;
        // Top edge of the sprite.
        public final int y;

        // Creates a placement.
        Placement(Stitcher.Holder holder, int x, int y) {
            this.holder = holder;
            this.x = x;
            this.y = y;
        }
    }

    // Layout of every sprite and the atlas size it needs.
    public static final class Result {
        // Width of the atlas.
        public final int width;
        // Height of the atlas.
        public final int height;
        // Position of every sprite.
        public final List<Placement> placements;

        // Creates a result.
        Result(int width, int height, List<Placement> placements) {
            this.width = width;
            this.height = height;
            this.placements = placements;
        }
    }

    // Returns the smallest layout found and sorts the holders in place.
    public static Result stitch(Stitcher.Holder[] holders, int maxWidth, int maxHeight) {
        Arrays.sort(holders);
        long area = 0;
        int widest = 0;
        for (Stitcher.Holder holder : holders) {
            area += (long) holder.getWidth() * holder.getHeight();
            widest = Math.max(widest, holder.getWidth());
        }
        if (holders.length == 0) {
            return new Result(0, 0, new ArrayList<>());
        }
        int width = MathHelper.smallestEncompassingPowerOfTwo(Math.max(widest, (int) Math.ceil(Math.sqrt(area))));
        width = Math.min(width, maxWidth);
        Result best = null;
        while (true) {
            Result attempt = pack(holders, width);
            if (attempt != null && attempt.height <= maxHeight) {
                if (best == null || (long) attempt.width * attempt.height < (long) best.width * best.height) {
                    best = attempt;
                }
                if (attempt.height <= attempt.width || width >= maxWidth) {
                    return best;
                }
            } else if (best != null) {
                return best;
            }
            if (width >= maxWidth) {
                break;
            }
            width = (int) Math.min((long) width * 2, maxWidth);
        }
        if (best != null) {
            return best;
        }
        Stitcher.Holder culprit = holders[0];
        throw new StitcherException(culprit, String.format("Unable to fit: %s, size: %dx%d, atlasMax: %dx%d - Maybe try a lower resolution resourcepack?",
                culprit.getAtlasSprite().getIconName(), culprit.getAtlasSprite().getIconWidth(), culprit.getAtlasSprite().getIconHeight(), maxWidth, maxHeight));
    }

    // Returns the layout for one atlas width or null when a sprite is wider.
    private static Result pack(Stitcher.Holder[] holders, int width) {
        List<Shelf> shelves = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>();
        List<Placement> placements = new ArrayList<>(holders.length);
        int totalHeight = 0;
        for (Stitcher.Holder holder : holders) {
            int w = holder.getWidth();
            int h = holder.getHeight();
            if (w > width) {
                return null;
            }
            boolean placed = false;
            for (int i = 0, n = gaps.size(); i < n; i++) {
                Gap gap = gaps.get(i);
                if (w <= gap.width && h <= gap.height) {
                    placements.add(new Placement(holder, gap.x, gap.y));
                    if (gap.width > w) {
                        // Creates a gap.
                        gaps.add(new Gap(gap.x + w, gap.y, gap.width - w, h));
                    }
                    gap.y += h;
                    gap.height -= h;
                    if (gap.height == 0) {
                        gaps.remove(i);
                    }
                    placed = true;
                    break;
                }
            }
            if (placed) {
                continue;
            }
            for (Shelf shelf : shelves) {
                if (shelf.x + w <= width) {
                    placements.add(new Placement(holder, shelf.x, shelf.y));
                    if (shelf.height > h) {
                        gaps.add(new Gap(shelf.x, shelf.y + h, w, shelf.height - h));
                    }
                    shelf.x += w;
                    placed = true;
                    break;
                }
            }
            if (placed) {
                continue;
            }
            // Creates a row.
            Shelf shelf = new Shelf(totalHeight, h);
            placements.add(new Placement(holder, 0, shelf.y));
            shelf.x = w;
            shelves.add(shelf);
            totalHeight += h;
        }
        return new Result(width, MathHelper.smallestEncompassingPowerOfTwo(totalHeight), placements);
    }

    // Row of sprites.
    private static final class Shelf {
        // Top edge of the row.
        final int y;
        // Height of the tallest sprite of the row.
        final int height;
        // Left edge of the free space.
        int x;

        // Top edge.
        Shelf(int y, int height) {
            this.y = y;
            this.height = height;
        }
    }

    // Free rectangle below a short sprite.
    private static final class Gap {
        // Left edge.
        final int x;
        int y;
        // Width of the shelf.
        final int width;
        // Height of the tallest sprite on the shelf.
        int height;

        Gap(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
