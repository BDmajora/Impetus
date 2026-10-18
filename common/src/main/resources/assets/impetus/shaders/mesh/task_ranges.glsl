// Facing-group selection shared by the opaque task shaders; the includer declares the Task payload (origin, baseOffset, quadCount, rangeEnd/StartA/B) first

// How far a facing group may reach past the section-local box, which clamps out-of-section vertices into its edge cells
#define FACE_CULL_MARGIN 0.5

void appendRange(inout uint slot, inout uint runningIndex, uint offset, uint count) {
    uint id = slot++;

    if (id < 4u) {
        rangeEndA[id] = runningIndex + count;
        rangeStartA[id] = offset;
    } else {
        rangeEndB[id - 4u] = runningIndex + count;
        rangeStartB[id - 4u] = offset;
    }

    runningIndex += count;
}

// Keeps the facing groups the eye can see and writes their ranges and quadCount; the counts accumulate into offsets, so a section can hold more quads than a 16-bit offset reaches
void selectRanges(ivec4 header, ivec3 relativeChunk, uvec4 ranges, bool cullByFacing) {
    uint slot = 0u;
    uint runningIndex = 0u;

    rangeEndA = uvec4(0);
    rangeEndB = uvec4(0);

    // A face looking along +axis is only seen from beyond the lowest one, so the eye is compared with the section's geometry box rather than its whole cube
    vec3 eye = -subChunkOffset.xyz - vec3(relativeChunk << 4);
    vec3 low = vec3(header.xyz & 0xF);
    vec3 high = low + vec3((header.xyz >> 4) & 0xF) + 1.0;
    bvec3 positive = cullByFacing ? greaterThan(eye, low - FACE_CULL_MARGIN) : bvec3(true);
    bvec3 negative = cullByFacing ? lessThan(eye, high + FACE_CULL_MARGIN) : bvec3(true);

    // The opaque groups start after the translucent quads
    uint cursor = (ranges.w >> 16) & 0xFFFFu;

    uint count = ranges.x & 0xFFFFu;
    if (positive.x && count > 0u) { appendRange(slot, runningIndex, cursor, count); }
    cursor += count;

    count = (ranges.x >> 16) & 0xFFFFu;
    if (positive.y && count > 0u) { appendRange(slot, runningIndex, cursor, count); }
    cursor += count;

    count = ranges.y & 0xFFFFu;
    if (positive.z && count > 0u) { appendRange(slot, runningIndex, cursor, count); }
    cursor += count;

    count = (ranges.y >> 16) & 0xFFFFu;
    if (negative.x && count > 0u) { appendRange(slot, runningIndex, cursor, count); }
    cursor += count;

    count = ranges.z & 0xFFFFu;
    if (negative.y && count > 0u) { appendRange(slot, runningIndex, cursor, count); }
    cursor += count;

    count = (ranges.z >> 16) & 0xFFFFu;
    if (negative.z && count > 0u) { appendRange(slot, runningIndex, cursor, count); }
    cursor += count;

    // Unassigned quads have no single facing, so they always draw
    appendRange(slot, runningIndex, cursor, ranges.w & 0xFFFFu);

    quadCount = runningIndex;
}
