package network.azusake.halo.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import network.azusake.halo.mixin.BufferBuilderStorageAccessor;
import org.lwjgl.system.MemoryUtil;

/** BufferBuilder's native malloc has no cleaner; upload staging must have an explicit owner. */
final class MeshUploadBuffer extends BufferBuilder implements AutoCloseable {
    private boolean closed;
    MeshUploadBuffer(int capacity) { super(capacity); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        // Match GlAllocationUtils, including the latest allocation after builder growth, but take
        // the buffer out of the builder first: the address must not stay reachable after it is
        // returned to the allocator, or any later cleanup path (for example ModernFix's
        // BufferBuilder.finalize mixin) frees this same block a second time and crashes jemalloc.
        BufferBuilderStorageAccessor accessor = (BufferBuilderStorageAccessor) (Object) this;
        ByteBuffer storage = accessor.halo$storage();
        accessor.halo$setStorage(null);
        if (storage != null) {
            MemoryUtil.getAllocator(false).free(MemoryUtil.memAddress0(storage));
        }
    }
}
