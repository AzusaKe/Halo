package network.azusake.halo.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1-only bridge for binding Halo-owned indexed mesh buffers to vanilla VertexBuffer draws. */
@Mixin(VertexBuffer.class)
public interface VertexBufferAccessor {
    @Accessor("indexCount") void halo$setIndexCount(int value);
    @Accessor("indexType") void halo$setIndexType(VertexFormat.IndexType value);
    @Accessor("sharedSequentialIndexBuffer")
    void halo$setSharedSequentialIndexBuffer(RenderSystem.ShapeIndexBuffer value);
}
