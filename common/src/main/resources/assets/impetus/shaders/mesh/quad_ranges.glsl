// The mesh-side half of task_ranges.glsl; the includer declares the matching Task payload first

// A meshlet-wide quad index to its quad in the arena by walking the ranges the task shader kept, or uint(-1) past the end
uint resolveQuad(uint index) {
    if (index < rangeEndA.x) { return rangeStartA.x + index + baseOffset; }
    if (index < rangeEndA.y) { return rangeStartA.y + (index - rangeEndA.x) + baseOffset; }
    if (index < rangeEndA.z) { return rangeStartA.z + (index - rangeEndA.y) + baseOffset; }
    if (index < rangeEndA.w) { return rangeStartA.w + (index - rangeEndA.z) + baseOffset; }
    if (index < rangeEndB.x) { return rangeStartB.x + (index - rangeEndA.w) + baseOffset; }
    if (index < rangeEndB.y) { return rangeStartB.y + (index - rangeEndB.x) + baseOffset; }
    if (index < rangeEndB.z) { return rangeStartB.z + (index - rangeEndB.y) + baseOffset; }
    if (index < rangeEndB.w) { return rangeStartB.w + (index - rangeEndB.z) + baseOffset; }

    return uint(-1);
}
