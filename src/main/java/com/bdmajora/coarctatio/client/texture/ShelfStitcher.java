package com.bdmajora.coarctatio.client.texture;

import net.minecraft.client.renderer.StitcherException;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// A shelf packer for the texture atlas (the idea behind VintageFix's TurboStitcher): vanilla places each sprite by walking every slot placed so far and recursively splitting the first that fits, which on a pack with tens of thousands of sprites is seconds of stitching per reload. Sprites are laid in rows of decreasing height, and the gap above a short sprite in a tall row is refilled by later, smaller sprites, which for power-of-two sizes recovers nearly all the waste. The atlas width starts at the square root of the total area and doubles until the rows fit the height limit
public final class ShelfStitcher {
    private ShelfStitcher() {
    }

    // One placed sprite: its holder and the atlas cell it occupies
    public static final class Placement {
        public final Stitcher.Holder holder;
        public final int x;
        public final int y;

        Placement(Stitcher.Holder holder, int x, int y) {
            this.holder = holder;
            this.x = x;
            this.y = y;
        }
    }

    public static final class Result {
        public final int width;
        public final int height;
        public final List<Placement> placements;

        Result(int width, int height, List<Placement> placements) {
            this.width = width;
            this.height = height;
            this.placements = placements;
        }
    }

    public static Result stitch(Stitcher.Holder[] holders, int maxWidth, int maxHeight) {
        // Vanilla's own ordering: tallest first, then widest, then by name, so the layout is deterministic across reloads
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
                // A taller-than-wide atlas is worth one more try at double width, which usually halves the height and sometimes the area
                if (attempt.height <= attempt.width || width >= maxWidth) {
                    return best;
                }
            } else if (best != null) {
                return best;
            }
            if (width >= maxWidth) {
                break;
            }
            width = Math.min(width * 2, maxWidth);
        }
        if (best != null) {
            return best;
        }
        Stitcher.Holder culprit = holders[0];
        throw new StitcherException(culprit, String.format("Unable to fit: %s, size: %dx%d, atlasMax: %dx%d - Maybe try a lower resolution resourcepack?",
                culprit.getAtlasSprite().getIconName(), culprit.getAtlasSprite().getIconWidth(), culprit.getAtlasSprite().getIconHeight(), maxWidth, maxHeight));
    }

    // First-fit decreasing height with gap refill; null when a sprite is wider than the atlas
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
            // Gaps above earlier, shorter sprites first: filling them costs no new atlas area
            boolean placed = false;
            for (int i = 0, n = gaps.size(); i < n; i++) {
                Gap gap = gaps.get(i);
                if (w <= gap.width && h <= gap.height) {
                    placements.add(new Placement(holder, gap.x, gap.y));
                    // What is left beside and above the sprite inside the gap
                    if (gap.width > w) {
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
            Shelf shelf = new Shelf(totalHeight, h);
            placements.add(new Placement(holder, 0, shelf.y));
            shelf.x = w;
            shelves.add(shelf);
            totalHeight += h;
        }
        return new Result(width, MathHelper.smallestEncompassingPowerOfTwo(totalHeight), placements);
    }

    private static final class Shelf {
        final int y;
        final int height;
        int x;

        Shelf(int y, int height) {
            this.y = y;
            this.height = height;
        }
    }

    private static final class Gap {
        final int x;
        int y;
        final int width;
        int height;

        Gap(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
