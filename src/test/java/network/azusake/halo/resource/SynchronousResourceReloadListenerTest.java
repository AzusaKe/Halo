package network.azusake.halo.resource;

import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SynchronousResourceReloadListenerTest {
    private static final ResourceManager RESOURCES = (ResourceManager) Proxy.newProxyInstance(
        ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
        (proxy, method, args) -> { throw new AssertionError("Unexpected resource access: " + method); });

    @Test void publicationWaitsForPreparationAndRunsOnTheGameExecutor() throws Exception {
        var gate = new CompletableFuture<Void>();
        var applied = new AtomicReference<Thread>();
        var owner = new AtomicReference<Thread>();
        try (var executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "halo-reload-test");
            owner.set(thread);
            return thread;
        })) {
            var listener = new SynchronousResourceReloadListener(Identifier.fromNamespaceAndPath("halo", "test")) {
                @Override protected void load(ResourceManager manager) {
                    assertSame(RESOURCES, manager);
                    applied.set(Thread.currentThread());
                }
            };
            var pending = listener.reload(new PreparableReloadListener.SharedState(RESOURCES),
                task -> fail("Synchronous loading must not publish on the background executor"), barrier(gate), executor);
            assertNull(applied.get());
            assertFalse(pending.isDone());
            gate.complete(null);
            pending.get(5, TimeUnit.SECONDS);
            assertSame(owner.get(), applied.get());
            assertNotSame(Thread.currentThread(), applied.get());
        }
    }

    @Test void failedApplyIsNotReportedAsSuccessfulReload() {
        var failure = new IllegalStateException("fixture apply failure");
        var listener = new SynchronousResourceReloadListener(Identifier.fromNamespaceAndPath("halo", "failure")) {
            @Override protected void load(ResourceManager manager) { throw failure; }
        };
        var result = listener.reload(new PreparableReloadListener.SharedState(RESOURCES), Runnable::run,
            barrier(CompletableFuture.completedFuture(null)), Runnable::run);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
    }

    private static PreparableReloadListener.PreparationBarrier barrier(CompletableFuture<Void> gate) {
        return new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T value) { return gate.thenApply(ignored -> value); }
        };
    }
}
