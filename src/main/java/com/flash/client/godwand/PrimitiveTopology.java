package com.flash.client.godwand;

import com.mojang.blaze3d.vertex.VertexFormat;

/** God Wand's primitive kinds, as 1.20.1's vertex modes. */
public enum PrimitiveTopology {
    QUADS(VertexFormat.Mode.QUADS),
    TRIANGLES(VertexFormat.Mode.TRIANGLES),
    TRIANGLE_STRIP(VertexFormat.Mode.TRIANGLE_STRIP),
    TRIANGLE_FAN(VertexFormat.Mode.TRIANGLE_FAN);

    final VertexFormat.Mode mode;

    PrimitiveTopology(VertexFormat.Mode mode) {
        this.mode = mode;
    }
}
