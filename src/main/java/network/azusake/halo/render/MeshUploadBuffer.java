package network.azusake.halo.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

/** Owns the 1.21 ByteBufferBuilder used for one immutable GPU upload. */
final class MeshUploadBuffer implements AutoCloseable {
    private final ByteBufferBuilder allocator;

    MeshUploadBuffer(int capacity) {
        allocator = new ByteBufferBuilder(capacity);
    }

    BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        return new BufferBuilder(allocator, mode, format);
    }

    @Override public void close() { allocator.close(); }
}
