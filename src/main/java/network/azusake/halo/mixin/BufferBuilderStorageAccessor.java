package network.azusake.halo.mixin;

import java.nio.ByteBuffer;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Only used to release Halo-owned, one-shot upload builders after synchronous GPU upload.
 *
 * <p>The write accessor exists so {@code MeshUploadBuffer.close()} can atomically take the
 * buffer out of the builder before freeing it: a plain read would leave the native address
 * in place, and the JVM's own cleanup paths ({@code BufferBuilder} has no cleaner of its own,
 * but third-party mixins such as ModernFix's {@code BufferBuilder.finalize} do) would free the
 * same address a second time.
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderStorageAccessor {
    @Accessor("buffer") ByteBuffer halo$storage();

    @Accessor("buffer") void halo$setStorage(ByteBuffer buffer);
}
