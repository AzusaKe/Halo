package network.azusake.halo.render;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.BufferAllocator;

/** Owns the 1.21 BufferAllocator used for one immutable GPU upload. */
final class MeshUploadBuffer implements AutoCloseable {
    private final BufferAllocator allocator;

    MeshUploadBuffer(int capacity) {
        allocator = new BufferAllocator(capacity);
    }

    BufferBuilder begin(VertexFormat.DrawMode mode, VertexFormat format) {
        return new BufferBuilder(allocator, mode, format);
    }

    @Override public void close() { allocator.close(); }
}
