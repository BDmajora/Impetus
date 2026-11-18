package com.bdmajora.extras.client.culling;

// Camera point of one frame that the worker casts its rays from.
public record OcclusionCamera(double x, double y, double z) {
}
