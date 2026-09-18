package network.azusake.halo.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import network.azusake.halo.mixin.BufferBuilderStorageAccessor;
import org.lwjgl.system.MemoryUtil;

/** BufferBuilder's native malloc has no cleaner; upload staging must have an explicit owner. */
final class MeshUploadBuffer extends BufferBuilder implements AutoCloseable {
    private boolean closed;
    MeshUploadBuffer(int capacity) { super(capacity); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        // Match GlAllocationUtils, including the latest allocation after builder growth.
        MemoryUtil.getAllocator(false).free(MemoryUtil.memAddress0(((BufferBuilderStorageAccessor)(Object)this).halo$storage()));
    }
}
