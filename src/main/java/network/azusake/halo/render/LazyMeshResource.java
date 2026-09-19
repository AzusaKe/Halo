package network.azusake.halo.render;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** One upload attempt per layout and resource generation, including remembered failures. */
final class LazyMeshResource<T extends GenerationMeshCache.Owned> implements GenerationMeshCache.Owned {
    private T value;
    private boolean attempted;

    T get(Supplier<T> upload, Consumer<Throwable> failure) {
        if (!attempted) {
            attempted = true;
            try { value = upload.get(); }
            catch (RuntimeException | OutOfMemoryError error) { failure.accept(error); }
        }
        return value;
    }

    public void close() {
        if (value != null) value.close();
        value = null;
        attempted = true;
    }
}
