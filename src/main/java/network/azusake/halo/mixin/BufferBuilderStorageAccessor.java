package network.azusake.halo.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only used to release Halo-owned, one-shot upload builders after synchronous GPU upload. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderStorageAccessor {
    @Accessor("buffer") ByteBuffer halo$storage();
}
