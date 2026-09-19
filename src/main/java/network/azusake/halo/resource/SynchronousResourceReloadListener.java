package network.azusake.halo.resource;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 26.2 移除了 Fabric v0 的 {@code ResourceManagerHelper} /
 * {@code SimpleSynchronousResourceReloadListener}。本适配器基于原版
 * {@link PreparableReloadListener} 复刻旧 v0 API 的语义：
 * 准备阶段等待（barrier）后，在游戏线程同步执行加载。
 */
public abstract class SynchronousResourceReloadListener implements PreparableReloadListener {

    private final Identifier id;

    protected SynchronousResourceReloadListener(Identifier id) {
        this.id = id;
    }

    /** 注册用的唯一标识（替代旧接口的 {@code getFabricId()}）。 */
    public final Identifier id() {
        return id;
    }

    @Override
    public final CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
                                                PreparationBarrier preparationBarrier, Executor gameExecutor) {
        ResourceManager manager = sharedState.resourceManager();
        return CompletableFuture.completedFuture(null)
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(unused -> load(manager), gameExecutor);
    }

    @Override
    public String getName() {
        return id.toString();
    }

    /** 在原版资源重载的 apply 阶段、游戏线程上执行。 */
    protected abstract void load(ResourceManager manager);
}
