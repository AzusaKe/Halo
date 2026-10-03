package network.azusake.halo.compat.caustica;

import dev.comfyfluffy.caustica.api.resource.*;
import dev.comfyfluffy.caustica.api.session.RenderSessionContext;
import dev.comfyfluffy.caustica.api.vulkan.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.*;
import java.nio.*;
import java.util.concurrent.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.util.vma.Vma.*;

/** Provider-owned allocations. Publication always follows initialization and retains immutable bytes. */
final class CausticaGpu {
    private final RenderSessionContext context;
    CausticaGpu(RenderSessionContext context) { this.context=context; }
    private static void check(int code) { if(code!=VK_SUCCESS)throw new IllegalStateException("Vulkan result "+code); }
    final class Buffer implements AutoCloseable {
        final long handle,allocation,address;final ByteBuffer bytes;final int size;
        Buffer(int size,int usage) {
            this.size=size;
            try(var stack=MemoryStack.stackPush()) {
                var info=VkBufferCreateInfo.calloc(stack).sType$Default().size(size).usage(usage|VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
                var families=context.gpu().asyncBufferSharingQueueFamilies();
                if(families.length>1)info.sharingMode(VK_SHARING_MODE_CONCURRENT).pQueueFamilyIndices(stack.ints(families));
                var alloc=VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO)
                    .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT|VMA_ALLOCATION_CREATE_MAPPED_BIT)
                    .requiredFlags(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
                var output=stack.mallocLong(1);var memory=stack.mallocPointer(1);var mapped=VmaAllocationInfo.calloc(stack);
                check(vmaCreateBuffer(context.gpu().vmaAllocator(),info,alloc,output,memory,mapped));
                handle=output.get(0);allocation=memory.get(0);
                bytes=MemoryUtil.memByteBuffer(mapped.pMappedData(),size).order(ByteOrder.LITTLE_ENDIAN);
                address=vkGetBufferDeviceAddress(context.gpu().vk(),VkBufferDeviceAddressInfo.calloc(stack).sType$Default().buffer(handle));
                CausticaMetrics.liveBuffers.increment();
            }
        }
        void flush() { vmaFlushAllocation(context.gpu().vmaAllocator(),allocation,0,size); }
        @Override public void close() { vmaDestroyBuffer(context.gpu().vmaAllocator(),handle,allocation);CausticaMetrics.liveBuffers.decrement(); }
    }
    Buffer buffer(int size) { return new Buffer(size,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT|VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR|VK_BUFFER_USAGE_TRANSFER_SRC_BIT); }
    record Texture(int index,boolean opaque,ResourceOwner owner) { }
    CompletableFuture<Texture> texture(int width,int height,ByteBuffer pixels,boolean srgb,boolean opaque) {
        long image,allocation;GpuDescriptorRange<GpuDescriptorIndex.Resource> descriptor;
        Buffer staging=buffer(pixels.remaining());staging.bytes.put(pixels);staging.flush();
        try(var stack=MemoryStack.stackPush()) {
            var info=VkImageCreateInfo.calloc(stack).sType$Default().imageType(VK_IMAGE_TYPE_2D)
                .format(srgb?VK_FORMAT_R8G8B8A8_SRGB:VK_FORMAT_R8G8B8A8_UNORM).mipLevels(1).arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            info.extent().set(width,height,1);
            int[] families=context.gpu().asyncBufferSharingQueueFamilies();
            if(families.length>1)info.sharingMode(VK_SHARING_MODE_CONCURRENT).pQueueFamilyIndices(stack.ints(families));
            var output=stack.mallocLong(1);var memory=stack.mallocPointer(1);
            try { check(vmaCreateImage(context.gpu().vmaAllocator(),info,VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE),output,memory,null)); }
            catch(Throwable error) { staging.close();throw error; }
            image=output.get(0);allocation=memory.get(0);CausticaMetrics.liveImages.increment();
            try { descriptor=context.gpu().descriptorHeap().allocateResources(1); }
            catch(Throwable failure) { vmaDestroyImage(context.gpu().vmaAllocator(),image,allocation);CausticaMetrics.liveImages.decrement();staging.close();throw failure; }
            try {
                var view=VkImageViewCreateInfo.calloc(stack).sType$Default().image(image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(info.format());
                view.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                var desc=VkImageDescriptorInfoEXT.calloc(stack).sType$Default().pView(view).layout(VK_IMAGE_LAYOUT_GENERAL);
                context.gpu().descriptorHeap().writer().writeResource(descriptor,0,VkResourceDescriptorInfoEXT.calloc(stack).sType$Default()
                    .type(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE).data(data -> data.pImage(desc)));
            } catch(Throwable failure) { descriptor.destroy();vmaDestroyImage(context.gpu().vmaAllocator(),image,allocation);CausticaMetrics.liveImages.decrement();staging.close();throw failure; }
        }
        var resource=context.resources().create(() -> {
            descriptor.destroy();vmaDestroyImage(context.gpu().vmaAllocator(),image,allocation);CausticaMetrics.liveImages.decrement();
        });
        var result=new CompletableFuture<Texture>();
        var uploadClaim=resource.retain();
        try {
            context.compute().submit(command -> {
                try(var stack=MemoryStack.stackPush()) {
                    var barrier=VkImageMemoryBarrier2.calloc(1,stack).sType$Default().image(image)
                        .srcStageMask(VK_PIPELINE_STAGE_2_NONE).srcAccessMask(0).dstStageMask(VK_PIPELINE_STAGE_2_COPY_BIT)
                        .dstAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT).oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
                    barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
                    vkCmdPipelineBarrier2(command,VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
                    var copy=VkBufferImageCopy.calloc(1,stack);copy.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                    copy.imageExtent().set(width,height,1);
                    vkCmdCopyBufferToImage(command,staging.handle,image,VK_IMAGE_LAYOUT_GENERAL,copy);
                    barrier.srcStageMask(VK_PIPELINE_STAGE_2_COPY_BIT).srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                        .dstStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT).dstAccessMask(VK_ACCESS_2_SHADER_SAMPLED_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_GENERAL);
                    vkCmdPipelineBarrier2(command,VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
                }
            },java.util.List.of(staging,uploadClaim),completion -> {
                if(completion instanceof GpuComputeCompletion.Succeeded) {
                    CausticaMetrics.textureBytes.add((long)width*height*4);result.complete(new Texture(descriptor.firstIndex().value(),opaque,resource));
                } else { resource.close();result.completeExceptionally(new IllegalStateException("Halo texture upload "+completion)); }
            });
        } catch(Throwable failure) { staging.close();uploadClaim.close();resource.close();result.completeExceptionally(failure); }
        return result;
    }
    final class Parameters implements AutoCloseable {
        private final ConcurrentLinkedQueue<Buffer> free=new ConcurrentLinkedQueue<>();
        private volatile boolean closed;
        Page page(ResourceOwner dependency) { if(closed)throw new IllegalStateException("Closed parameter pool");var buffer=free.poll();return new Page(buffer==null?buffer(32768):buffer,dependency); }
        final class Page implements AutoCloseable {
            final Buffer buffer;final ResourceOwner owner;int used;
            Page(Buffer buffer,ResourceOwner dependency) { this.buffer=buffer;var claim=dependency.retain();owner=context.resources().create(() -> { recycle(buffer);claim.close(); }); }
            long write(ByteBuffer value) { int size=value.remaining();if(used+size>buffer.size)throw new IllegalStateException("Parameter page full");long address=buffer.address+used;
                buffer.bytes.position(used);buffer.bytes.put(value);used+=size;return address; }
            void flush() { buffer.flush(); }
            @Override public void close() { owner.close(); }
        }
        private synchronized void recycle(Buffer buffer) { if(closed)buffer.close();else free.add(buffer); }
        @Override public synchronized void close() { closed=true;Buffer buffer;while((buffer=free.poll())!=null)buffer.close(); }
    }
}
