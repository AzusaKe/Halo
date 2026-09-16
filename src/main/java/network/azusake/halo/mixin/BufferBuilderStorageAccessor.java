package network.azusake.halo.mixin;

import java.nio.ByteBuffer;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only used to release Halo-owned, one-shot upload builders after synchronous GPU upload. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderStorageAccessor {
    @Accessor("buffer") ByteBuffer halo$storage();
}
