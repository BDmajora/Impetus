package com.bdmajora.extras.client.itemfaces;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.ItemFrameLodState;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.BlockPartRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.block.model.SimpleBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemFacesTest {
    private static TextureAtlasSprite sprite;
    private ExtrasConfig config;

    @BeforeAll
    static void atlas() {
        Mc.bootstrap();
        // A slot spanning the whole sheet, so a baked texture coordinate is the model's over sixteen
        sprite = Mc.uninitialized(TextureAtlasSprite.class);
        Mixins.set(sprite, "maxU", 1.0F);
        Mixins.set(sprite, "maxV", 1.0F);
    }

    @BeforeEach
    void freshConfig() {
        config = new ExtrasConfig();
        Mixins.set(Extras.class, "config", config);
    }

    @AfterEach
    void forget() {
        Mixins.set(Extras.class, "config", null);
        ItemFaceCache.clear();
    }

    // One element face as vanilla's FaceBakery bakes it, bounds in model pixels like a JSON model, the tint telling faces apart
    private static BakedQuad face(EnumFacing facing, float x0, float y0, float z0, float x1, float y1, float z1, int tint) {
        return face(facing, new Vector3f(x0, y0, z0), new Vector3f(x1, y1, z1), tint, null);
    }

    private static BakedQuad face(EnumFacing facing, Vector3f from, Vector3f to, int tint, BlockPartRotation rotation) {
        BlockPartFace part = new BlockPartFace(null, tint, "#all", new BlockFaceUV(new float[]{0.0F, 0.0F, 16.0F, 16.0F}, 0));
        return new FaceBakery().makeBakedQuad(from, to, part, sprite, facing, ModelRotation.X0_Y0, rotation, false, true);
    }

    // The six faces of one element
    private static List<BakedQuad> cube(float x0, float y0, float z0, float x1, float y1, float z1) {
        List<BakedQuad> faces = new ArrayList<>();
        for (EnumFacing side : EnumFacing.VALUES) {
            faces.add(face(side, x0, y0, z0, x1, y1, z1, 0));
        }
        return faces;
    }

    // Seven lists in draw order with every quad in the general one
    private static List<List<BakedQuad>> general(List<BakedQuad> quads) {
        List<List<BakedQuad>> lists = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            lists.add(List.of());
        }
        lists.add(quads);
        return lists;
    }

    private static List<List<BakedQuad>> general(BakedQuad... quads) {
        return general(List.of(quads));
    }

    // The resolved general list of a model whose quads were all general, checking the six cull lists stayed empty
    private static List<BakedQuad> resolved(BakedQuad... quads) {
        List<List<BakedQuad>> lists = CoplanarFaces.resolve(general(quads));
        assertNotNull(lists);
        assertEquals(CoplanarFaces.LISTS, lists.size());
        for (int i = 0; i < 6; i++) {
            assertTrue(lists.get(i).isEmpty());
        }
        return lists.get(6);
    }

    // A quad built straight from vertex data in the item format
    private static BakedQuad plain(int[] data) {
        return new BakedQuad(data, 1, EnumFacing.NORTH, sprite, true, DefaultVertexFormats.ITEM);
    }

    private static float[] position(BakedQuad quad, int vertex) {
        int[] data = quad.getVertexData();
        int at = vertex * quad.getFormat().getIntegerSize();
        return new float[]{Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2])};
    }

    private static float[] uv(BakedQuad quad, int vertex) {
        int[] data = quad.getVertexData();
        int at = vertex * quad.getFormat().getIntegerSize() + 4;
        return new float[]{Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1])};
    }

    // Low and high coordinate on x, y and z in turn
    private static float[] bounds(BakedQuad quad) {
        float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int vertex = 0; vertex < 4; vertex++) {
            float[] at = position(quad, vertex);
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis * 2] = Math.min(bounds[axis * 2], at[axis]);
                bounds[axis * 2 + 1] = Math.max(bounds[axis * 2 + 1], at[axis]);
            }
        }
        return bounds;
    }

    // The side the winding faces, which is what culling and the rasterizer go by
    private static EnumFacing facing(BakedQuad quad) {
        float[] a = position(quad, 0);
        float[] b = position(quad, 1);
        float[] c = position(quad, 2);
        float[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float[] v = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        return EnumFacing.getFacingFromVector(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]);
    }

    // Every vertex of the quad sits at this coordinate on the axis
    private static void assertPlane(BakedQuad quad, int axis, float plane) {
        for (int vertex = 0; vertex < 4; vertex++) {
            assertEquals(plane, position(quad, vertex)[axis]);
        }
    }

    // Two pieces only rasterize to the same depths if their positions match bit for bit, in the same vertex order
    private static void assertSamePositions(BakedQuad expected, BakedQuad actual) {
        for (int vertex = 0; vertex < 4; vertex++) {
            assertArrayEquals(position(expected, vertex), position(actual, vertex));
        }
    }

    // The vertex of the quad at the given position, ignoring one axis (or none for -1)
    private static int vertexAt(BakedQuad quad, float[] at, int ignored) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float[] here = position(quad, vertex);
            boolean same = true;
            for (int axis = 0; axis < 3; axis++) {
                same &= axis == ignored || here[axis] == at[axis];
            }
            if (same) {
                return vertex;
            }
        }
        return fail("no vertex at " + Arrays.toString(at));
    }

    // The body piece of a north-facing stack whose low corner is at the given x and y
    private static BakedQuad cellAt(List<BakedQuad> quads, float x, float y) {
        return quads.stream()
                .filter(quad -> quad.getTintIndex() == 0 && bounds(quad)[0] == x && bounds(quad)[2] == y)
                .findFirst().orElseThrow();
    }

    private static List<Integer> tints(List<BakedQuad> quads) {
        return quads.stream().map(BakedQuad::getTintIndex).toList();
    }

    @Test
    void aPanelLaidOnABodyCutsTheBodyOnItsEdgesAndDrawsOverIt() {
        // Listed before the body, so only lying inside the body's face can put the panel on top
        BakedQuad panel = face(EnumFacing.NORTH, 4, 4, 0, 12, 12, 1, 1);
        BakedQuad body = face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0);
        BakedQuad back = face(EnumFacing.SOUTH, 0, 0, 0, 16, 16, 16, 0);
        List<BakedQuad> quads = resolved(panel, body, back);

        // The panel's slot gives its face up, the body's slot takes the whole stack, the back face is left as it was
        assertEquals(11, quads.size());
        assertSame(back, quads.get(10));
        BakedQuad top = quads.get(9);
        assertEquals(1, top.getTintIndex());
        List<BakedQuad> cells = quads.subList(0, 9);
        double area = 0.0D;
        int[] source = body.getVertexData();
        // The body's mapping read off its corners, since Forge's FaceBakery pulls every UV a thousandth in from its corner
        float[] low = uv(body, vertexAt(body, new float[]{0.0F, 0.0F, 0.0F}, -1));
        float[] high = uv(body, vertexAt(body, new float[]{1.0F, 1.0F, 0.0F}, -1));
        for (BakedQuad cell : cells) {
            assertEquals(0, cell.getTintIndex());
            assertEquals(EnumFacing.NORTH, facing(cell));
            assertPlane(cell, 2, 0.0F);
            float[] bounds = bounds(cell);
            area += (bounds[1] - bounds[0]) * (bounds[3] - bounds[2]);
            int[] data = cell.getVertexData();
            for (int vertex = 0; vertex < 4; vertex++) {
                // Texture coordinates keep the body's own mapping: on a north face u follows x alone and v follows y alone
                float[] at = position(cell, vertex);
                assertEquals(low[0] + (high[0] - low[0]) * at[0], uv(cell, vertex)[0], 1.0E-6F);
                assertEquals(low[1] + (high[1] - low[1]) * at[1], uv(cell, vertex)[1], 1.0E-6F);
                // Colour and the packed normal come over untouched
                assertEquals(source[3], data[vertex * 7 + 3]);
                assertEquals(source[6], data[vertex * 7 + 6]);
            }
            assertSame(sprite, cell.getSprite());
        }
        // Nine cells tile the body's face without overlapping
        assertEquals(1.0D, area, 1.0E-6D);
        for (int i = 0; i < cells.size(); i++) {
            for (int j = i + 1; j < cells.size(); j++) {
                float[] a = bounds(cells.get(i));
                float[] b = bounds(cells.get(j));
                assertFalse(Math.min(a[1], b[1]) > Math.max(a[0], b[0]) && Math.min(a[3], b[3]) > Math.max(a[2], b[2]));
            }
        }
        // A body corner keeps the body's corner texture coordinates exactly
        BakedQuad corner = cellAt(cells, 0.75F, 0.75F);
        int topRight = vertexAt(corner, new float[]{1.0F, 1.0F, 0.0F}, -1);
        assertArrayEquals(uv(body, vertexAt(body, new float[]{1.0F, 1.0F, 0.0F}, -1)), uv(corner, topRight));

        // The cell under the panel is the panel's rectangle vertex for vertex, so both rasterize to the same depths and GL_LEQUAL keeps the panel
        assertSamePositions(cellAt(cells, 0.25F, 0.25F), top);
        assertArrayEquals(bounds(panel), bounds(top));
        assertEquals(EnumFacing.NORTH, facing(top));
        for (int vertex = 0; vertex < 4; vertex++) {
            assertArrayEquals(uv(panel, vertexAt(panel, position(top, vertex), -1)), uv(top, vertex));
        }
        assertSame(sprite, top.getSprite());
        assertEquals(panel.getFace(), top.getFace());
        assertEquals(panel.shouldApplyDiffuseLighting(), top.shouldApplyDiffuseLighting());
        assertSame(panel.getFormat(), top.getFormat());
    }

    @Test
    void aBandTwoFacesShareGoesToWhicheverIsDrawnLast() {
        // The shulker box layout: base rows 0 to 8, lid rows 4 to 16, sharing rows 4 to 8 on one plane
        BakedQuad base = face(EnumFacing.WEST, 0, 0, 0, 16, 8, 16, 0);
        BakedQuad lid = face(EnumFacing.WEST, 0, 4, 0, 16, 16, 16, 1);
        List<BakedQuad> quads = resolved(base, lid);
        assertEquals(List.of(0, 0, 1, 1), tints(quads));
        assertSamePositions(quads.get(1), quads.get(2));
        assertEquals(0.25F, bounds(quads.get(1))[2]);
        assertEquals(0.5F, bounds(quads.get(1))[3]);
        for (BakedQuad quad : quads) {
            assertEquals(EnumFacing.WEST, facing(quad));
        }

        // Listed the other way round, the base wins the band
        quads = resolved(lid, base);
        assertEquals(List.of(1, 1, 0, 0), tints(quads));
        assertSamePositions(quads.get(0), quads.get(3));
    }

    @Test
    void facesAHairApartMeetOnTheBodysPlaneWithTheFrontOneOnTop() {
        BakedQuad body = face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0);
        // Raised a hundredth of a pixel toward the viewer (north is -z) and listed first: on top all the same, and on the body's plane
        BakedQuad raised = face(EnumFacing.NORTH, 4, 4, -0.01F, 12, 12, 1, 1);
        List<BakedQuad> quads = resolved(raised, body);
        assertEquals(10, quads.size());
        assertEquals(1, quads.get(9).getTintIndex());
        for (BakedQuad quad : quads) {
            assertPlane(quad, 2, 0.0F);
        }
        assertSamePositions(cellAt(quads, 0.25F, 0.25F), quads.get(9));

        // Sunk a hundredth of a pixel into the body, it stays under the body's face though it lies inside it
        BakedQuad sunk = face(EnumFacing.NORTH, 4, 4, 0.01F, 12, 12, 1, 1);
        quads = resolved(body, sunk);
        assertEquals(List.of(1, 0, 0, 0, 0, 0, 0, 0, 0, 0), tints(quads));
        for (BakedQuad quad : quads) {
            assertPlane(quad, 2, 0.0F);
        }
        assertSamePositions(cellAt(quads, 0.25F, 0.25F), quads.get(0));
    }

    @Test
    void edgesWithinFloatNoiseShareOneCut() {
        // A ten-thousandth of a pixel off the four-pixel line another panel starts on, as a zero-angle element rotation can leave an edge
        BakedQuad noisy = face(EnumFacing.NORTH, 4.0001F, 4, 0, 12, 12, 1, 1);
        BakedQuad strip = face(EnumFacing.NORTH, 4, 13, 0, 12, 15, 1, 2);
        BakedQuad body = face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0);
        List<BakedQuad> quads = resolved(noisy, strip, body);
        // Three columns, not four: the two edges are one cut, so neither panel splits and the body gets 3 x 5 cells
        assertEquals(17, quads.size());
        assertEquals(List.of(1, 2), tints(quads.subList(15, 17)));
        assertEquals(0.25F, bounds(quads.get(15))[0]);
        assertSamePositions(cellAt(quads, 0.25F, 0.25F), quads.get(15));
        assertSamePositions(cellAt(quads, 0.25F, 0.8125F), quads.get(16));
    }

    @Test
    void aFlatPanelsTwoSidesMoveApartAlongTheirOwnNormals() {
        BakedQuad front = face(EnumFacing.NORTH, 0, 0, 8, 16, 16, 8, 0);
        BakedQuad rear = face(EnumFacing.SOUTH, 0, 0, 8, 16, 16, 8, 1);
        List<BakedQuad> quads = resolved(front, rear);
        assertEquals(2, quads.size());
        float half = CoplanarFaces.SEPARATION / 2.0F;
        assertPlane(quads.get(0), 2, 0.5F - half);
        assertPlane(quads.get(1), 2, 0.5F + half);
        assertEquals(EnumFacing.NORTH, facing(quads.get(0)));
        assertEquals(EnumFacing.SOUTH, facing(quads.get(1)));
        // Only the depth moved
        for (int vertex = 0; vertex < 4; vertex++) {
            assertArrayEquals(uv(front, vertexAt(front, position(quads.get(0), vertex), 2)), uv(quads.get(0), vertex));
            assertArrayEquals(uv(rear, vertexAt(rear, position(quads.get(1), vertex), 2)), uv(quads.get(1), vertex));
        }
    }

    @Test
    void aDecalsRearFaceTakesTheWholeGapSoItsStackStaysOnTheBody() {
        BakedQuad body = face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0);
        BakedQuad decal = face(EnumFacing.NORTH, 4, 4, 0, 12, 12, 0, 1);
        BakedQuad rear = face(EnumFacing.SOUTH, 4, 4, 0, 12, 12, 0, 2);
        List<BakedQuad> quads = resolved(body, decal, rear);
        assertEquals(11, quads.size());
        for (int i = 0; i < 10; i++) {
            assertPlane(quads.get(i), 2, 0.0F);
        }
        assertEquals(1, quads.get(9).getTintIndex());
        assertEquals(2, quads.get(10).getTintIndex());
        assertPlane(quads.get(10), 2, CoplanarFaces.SEPARATION);
        assertEquals(EnumFacing.SOUTH, facing(quads.get(10)));
    }

    @Test
    void overlappingFlatPanelsMoveApartAsTwoStacks() {
        BakedQuad leftFront = face(EnumFacing.NORTH, 0, 0, 8, 12, 16, 8, 0);
        BakedQuad leftRear = face(EnumFacing.SOUTH, 0, 0, 8, 12, 16, 8, 0);
        BakedQuad rightFront = face(EnumFacing.NORTH, 4, 0, 8, 16, 16, 8, 1);
        BakedQuad rightRear = face(EnumFacing.SOUTH, 4, 0, 8, 16, 16, 8, 1);
        List<BakedQuad> quads = resolved(leftFront, leftRear, rightFront, rightRear);
        assertEquals(List.of(0, 0, 1, 1, 0, 0, 1, 1), tints(quads));
        float half = CoplanarFaces.SEPARATION / 2.0F;
        for (int i = 0; i < 4; i++) {
            assertPlane(quads.get(i), 2, 0.5F - half);
            assertEquals(EnumFacing.NORTH, facing(quads.get(i)));
            assertPlane(quads.get(i + 4), 2, 0.5F + half);
            assertEquals(EnumFacing.SOUTH, facing(quads.get(i + 4)));
        }
        assertSamePositions(quads.get(1), quads.get(2));
        assertSamePositions(quads.get(5), quads.get(6));
    }

    @Test
    void aStackSpreadOverListsLandsInTheListOfItsLastFace() {
        BakedQuad body = face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0);
        BakedQuad top = face(EnumFacing.UP, 0, 0, 0, 16, 16, 16, 0);
        BakedQuad panel = face(EnumFacing.NORTH, 4, 4, 0, 12, 12, 1, 1);
        List<List<BakedQuad>> lists = new ArrayList<>(general(panel));
        lists.set(EnumFacing.UP.getIndex(), List.of(top));
        lists.set(EnumFacing.NORTH.getIndex(), List.of(body));
        List<List<BakedQuad>> resolved = CoplanarFaces.resolve(lists);
        assertNotNull(resolved);
        assertEquals(List.of(top), resolved.get(EnumFacing.UP.getIndex()));
        assertTrue(resolved.get(EnumFacing.NORTH.getIndex()).isEmpty());
        assertEquals(10, resolved.get(6).size());
        assertEquals(1, resolved.get(6).get(9).getTintIndex());
    }

    @Test
    void identicalFacesKeepTheirOrderLikeAnOverlay() {
        BakedQuad side = face(EnumFacing.EAST, 0, 0, 0, 16, 16, 16, 0);
        BakedQuad overlay = face(EnumFacing.EAST, 0, 0, 0, 16, 16, 16, 1);
        List<BakedQuad> quads = resolved(side, overlay);
        assertEquals(List.of(0, 1), tints(quads));
        assertSamePositions(quads.get(0), quads.get(1));
        assertEquals(List.of(1, 0), tints(resolved(overlay, side)));
    }

    @Test
    void piecesFaceTheWayTheirFaceDidOnEverySide() {
        for (EnumFacing side : EnumFacing.VALUES) {
            BakedQuad body = face(side, 0, 0, 0, 16, 16, 16, 0);
            // A smaller element pushed out to the body's face on this side
            float[] from = {4, 4, 4};
            float[] to = {12, 12, 12};
            int axis = side.getAxis().ordinal();
            boolean positive = side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
            if (positive) {
                to[axis] = 16;
            } else {
                from[axis] = 0;
            }
            BakedQuad panel = face(side, new Vector3f(from[0], from[1], from[2]), new Vector3f(to[0], to[1], to[2]), 1, null);
            List<BakedQuad> quads = resolved(panel, body);
            assertEquals(10, quads.size(), side.getName());
            for (BakedQuad quad : quads) {
                assertEquals(side, facing(quad), side.getName());
                assertPlane(quad, axis, positive ? 1.0F : 0.0F);
            }
            assertEquals(1, quads.get(9).getTintIndex(), side.getName());
        }
    }

    @Test
    void modelsWithNothingToFightOverAreLeftAlone() {
        assertNull(CoplanarFaces.resolve(general()));
        assertNull(CoplanarFaces.resolve(general(cube(0, 0, 0, 16, 16, 16))));
        // Two boxes meeting along one edge share no area on any plane
        List<BakedQuad> pair = new ArrayList<>(cube(0, 0, 0, 8, 8, 16));
        pair.addAll(cube(8, 8, 0, 16, 16, 16));
        assertNull(CoplanarFaces.resolve(general(pair)));
        // A tenth of a pixel out is a plane of its own, which the depth buffer tells apart
        assertNull(CoplanarFaces.resolve(general(face(EnumFacing.NORTH, 4, 4, -0.1F, 12, 12, 1, 1),
                face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0))));
    }

    @Test
    void aStackThatWouldShatterIsLeftAsItWas() {
        List<BakedQuad> quads = new ArrayList<>();
        quads.add(face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0));
        // Thirty specks down the diagonal put 62 cuts on each axis, 3721 cells under the body alone
        for (int i = 0; i < 30; i++) {
            float at = 0.25F + i * 0.5F;
            quads.add(face(EnumFacing.NORTH, at, at, 0, at + 0.25F, at + 0.25F, 1, 1));
        }
        assertNull(CoplanarFaces.resolve(general(quads)));
    }

    @Test
    void onlyPlainAxisAlignedRectanglesTakePart() {
        BakedQuad body = face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0);
        BakedQuad panel = face(EnumFacing.NORTH, 4, 4, 0, 12, 12, 1, 1);
        assertNotNull(CoplanarFaces.resolve(general(panel, body)));

        // Each of these lies over the body's face like the panel, so taking part would cut the body
        int[] data = panel.getVertexData();
        List<BakedQuad> outsiders = new ArrayList<>();
        // Another quad class, which may compute its data its own way
        outsiders.add(new BakedQuad(data.clone(), 1, EnumFacing.NORTH, sprite, true, DefaultVertexFormats.ITEM) {
        });
        // A rotated element
        outsiders.add(face(EnumFacing.NORTH, new Vector3f(4, 4, 0), new Vector3f(12, 12, 1), 1,
                new BlockPartRotation(new Vector3f(0.5F, 0.5F, 0.5F), EnumFacing.Axis.Y, 22.5F, false)));
        // A format without texture coordinates
        int[] bare = new int[12];
        for (int vertex = 0; vertex < 4; vertex++) {
            System.arraycopy(data, vertex * 7, bare, vertex * 3, 3);
        }
        outsiders.add(new BakedQuad(bare, 1, EnumFacing.NORTH, sprite, true, DefaultVertexFormats.POSITION));
        // Vertex data of the wrong length
        outsiders.add(plain(Arrays.copyOf(data, 27)));
        // Flat on two axes, a line
        int[] line = data.clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            line[vertex * 7 + 1] = Float.floatToRawIntBits(0.5F);
        }
        outsiders.add(plain(line));
        // Folded over itself
        int[] folded = data.clone();
        System.arraycopy(data, 7, folded, 14, 7);
        System.arraycopy(data, 14, folded, 7, 7);
        outsiders.add(plain(folded));
        // A vertex partway along an edge
        int[] bent = data.clone();
        bent[0] = Float.floatToRawIntBits(0.5F);
        outsiders.add(plain(bent));
        // Two vertices on one corner
        int[] pinched = data.clone();
        System.arraycopy(data, 14, pinched, 21, 7);
        outsiders.add(plain(pinched));
        // Narrower than two merge distances
        outsiders.add(face(EnumFacing.NORTH, 4, 4, 0, 4.00024F, 12, 1, 1));
        // Not a number
        int[] broken = data.clone();
        broken[2] = Float.floatToRawIntBits(Float.NaN);
        outsiders.add(plain(broken));

        for (int i = 0; i < outsiders.size(); i++) {
            assertNull(CoplanarFaces.resolve(general(outsiders.get(i), body)), "outsider " + i);
        }
    }

    @Test
    void textureCoordinatesAreFoundOnlyAsFloatsAfterLeadingFloatPositions() {
        assertEquals(4, CoplanarFaces.uvOffset(DefaultVertexFormats.ITEM));
        assertEquals(4, CoplanarFaces.uvOffset(DefaultVertexFormats.BLOCK));
        assertEquals(3, CoplanarFaces.uvOffset(DefaultVertexFormats.POSITION_TEX));
        assertEquals(-1, CoplanarFaces.uvOffset(null));
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat()));
        assertEquals(-1, CoplanarFaces.uvOffset(DefaultVertexFormats.POSITION));
        // Colour leading
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat().addElement(DefaultVertexFormats.COLOR_4UB)
                .addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.TEX_2F)));
        // Positions as two floats, or as shorts
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat().addElement(new VertexFormatElement(0,
                VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.POSITION, 2)).addElement(DefaultVertexFormats.TEX_2F)));
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat().addElement(new VertexFormatElement(0,
                VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.POSITION, 3)).addElement(DefaultVertexFormats.TEX_2F)));
        // Texture coordinates as shorts, as one float, or off a whole int
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(new VertexFormatElement(0,
                VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2))));
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(new VertexFormatElement(0,
                VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 1))));
        assertEquals(-1, CoplanarFaces.uvOffset(new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F)
                .addElement(DefaultVertexFormats.PADDING_1B).addElement(DefaultVertexFormats.TEX_2F)));
        assertNotNull(Mixins.construct(CoplanarFaces.class));
    }

    // A resource pack model the way Forge bakes one: the body's north face in the north cull list, an inset panel and the back in the general list
    private static SimpleBakedModel panelled() {
        Map<EnumFacing, List<BakedQuad>> cull = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) {
            cull.put(side, new ArrayList<>());
        }
        cull.get(EnumFacing.NORTH).add(face(EnumFacing.NORTH, 0, 0, 0, 16, 16, 16, 0));
        List<BakedQuad> general = new ArrayList<>(List.of(face(EnumFacing.NORTH, 4, 4, 0, 12, 12, 1, 1),
                face(EnumFacing.SOUTH, 0, 0, 0, 16, 16, 16, 0)));
        return new SimpleBakedModel(general, cull, true, true, sprite, ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
    }

    // A lone cube, which has nothing to resolve
    private static SimpleBakedModel plainCube() {
        Map<EnumFacing, List<BakedQuad>> cull = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) {
            cull.put(side, new ArrayList<>());
        }
        return new SimpleBakedModel(new ArrayList<>(cube(0, 0, 0, 16, 16, 16)), cull, true, true, sprite,
                ItemCameraTransforms.DEFAULT, ItemOverrideList.NONE);
    }

    @Test
    void fixedModelsAnswerListsResolvedOnce() {
        SimpleBakedModel model = panelled();
        List<BakedQuad> general = ItemFaceCache.quads(model, null, null, 0L);
        // The stack moved into the panel's slot ahead of the untouched back face
        assertEquals(11, general.size());
        assertSame(model.getQuads(null, null, 0L).get(1), general.get(10));
        assertTrue(ItemFaceCache.quads(model, null, EnumFacing.NORTH, 0L).isEmpty());
        assertTrue(ItemFaceCache.quads(model, null, EnumFacing.UP, 0L).isEmpty());

        // Later frames get the same lists, from the last model or from the cache once another model came between
        assertSame(general, ItemFaceCache.quads(model, null, null, 0L));
        SimpleBakedModel other = panelled();
        assertNotSame(general, ItemFaceCache.quads(other, null, null, 0L));
        assertSame(general, ItemFaceCache.quads(model, null, null, 0L));
        // The item render hooks draw the resolved lists too
        assertSame(general, ItemFrameLodState.filterLodQuads(model, null, null, 0L));

        ItemFaceCache.clear();
        assertNotSame(general, ItemFaceCache.quads(model, null, null, 0L));
        assertNotNull(Mixins.construct(ItemFaceCache.class));
    }

    @Test
    void everythingElseIsDrawnAsTheModelAnswers() {
        SimpleBakedModel model = panelled();
        List<BakedQuad> own = model.getQuads(null, null, 0L);
        // A block state or a random seed is not the item path
        assertSame(own, ItemFaceCache.quads(model, Blocks.STONE.getDefaultState(), null, 0L));
        assertSame(own, ItemFaceCache.quads(model, null, null, 7L));
        config.render.itemModelFlickerFix = false;
        assertSame(own, ItemFaceCache.quads(model, null, null, 0L));
        config.render.itemModelFlickerFix = true;

        // A model class that may answer differently from call to call, and Forge's own wrapper, which can wrap one
        IBakedModel dynamic = mock(IBakedModel.class);
        when(dynamic.getQuads(any(), any(), anyLong())).thenReturn(own);
        assertSame(own, ItemFaceCache.quads(dynamic, null, null, 0L));
        assertSame(own, ItemFaceCache.quads(new PerspectiveMapWrapper(model, ImmutableMap.of()), null, null, 0L));

        // A fixed model with nothing to resolve is remembered as such and keeps answering its own lists
        SimpleBakedModel cube = plainCube();
        assertSame(cube.getQuads(null, null, 0L), ItemFaceCache.quads(cube, null, null, 0L));
        Map<IBakedModel, List<List<BakedQuad>>> resolved = Mixins.get(ItemFaceCache.class, "RESOLVED");
        assertEquals(List.of(), resolved.get(cube));
        assertSame(cube.getQuads(null, EnumFacing.UP, 0L), ItemFaceCache.quads(cube, null, EnumFacing.UP, 0L));
    }

    @Test
    void forgesWrapperAroundABakedJsonModelIsFixed() throws ClassNotFoundException {
        // Every resource pack item model reaches the renderer in this anonymous wrapper, which hands a null state to its SimpleBakedModel
        IBakedModel wrapper = (IBakedModel) Mc.uninitialized(Class.forName("net.minecraftforge.client.model.ModelLoader$VanillaModelWrapper$1"));
        Mixins.set(wrapper, "parent", panelled());
        List<BakedQuad> general = ItemFaceCache.quads(wrapper, null, null, 0L);
        assertEquals(11, general.size());
        assertNotSame(wrapper.getQuads(null, null, 0L), general);
    }
}
