package network.azusake.halo.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.render.TriangleMesh;

/** Owns adapter resources and reuses them only within one visual generation and mesh identity. */
final class GenerationMeshCache<T extends GenerationMeshCache.Owned> implements AutoCloseable {
    interface Owned { void close(); }
    @FunctionalInterface interface Factory<T> { T create(Identifier id, TriangleMesh mesh); }
    record Update<T extends Owned>(GenerationMeshCache<T> cache, int created) {}
    private record Entry<T>(TriangleMesh mesh, T value) {}

    private final long generation;
    private final Map<Identifier, Entry<T>> entries;
    private final Map<Identifier, TriangleMesh> failed;

    private GenerationMeshCache(long generation, Map<Identifier, Entry<T>> entries, Map<Identifier, TriangleMesh> failed) {
        this.generation = generation;
        this.entries = entries;
        this.failed = failed;
    }

    static <T extends Owned> GenerationMeshCache<T> empty() {
        return new GenerationMeshCache<>(Long.MIN_VALUE, Map.of(), Map.of());
    }

    Update<T> updated(long nextGeneration, Map<Identifier, TriangleMesh> meshes,
                      Factory<T> factory, BiConsumer<Identifier, Throwable> failure) {
        Objects.requireNonNull(meshes);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(failure);
        var next = new HashMap<Identifier, Entry<T>>();
        var nextFailed = new HashMap<Identifier, TriangleMesh>();
        int created = 0;
        for (var source : meshes.entrySet()) {
            if (generation == nextGeneration && failed.get(source.getKey()) == source.getValue()) {
                nextFailed.put(source.getKey(), source.getValue());
                continue;
            }
            Entry<T> retained = generation == nextGeneration ? entries.get(source.getKey()) : null;
            if (retained != null && retained.mesh() == source.getValue()) {
                next.put(source.getKey(), retained);
                continue;
            }
            try {
                next.put(source.getKey(), new Entry<>(source.getValue(), factory.create(source.getKey(), source.getValue())));
                created++;
            } catch (RuntimeException | OutOfMemoryError error) {
                nextFailed.put(source.getKey(), source.getValue());
                failure.accept(source.getKey(), error);
            }
        }
        var replacement = new GenerationMeshCache<T>(nextGeneration, Map.copyOf(next), Map.copyOf(nextFailed));
        closeExcept(replacement);
        return new Update<>(replacement, created);
    }

    long generation() { return generation; }
    T get(Identifier id) {
        Entry<T> entry = entries.get(id);
        return entry == null ? null : entry.value();
    }
    int size() { return entries.size(); }
    Iterable<T> values() { return entries.values().stream().map(Entry::value)::iterator; }

    private void closeExcept(GenerationMeshCache<T> retained) {
        for (var current : entries.entrySet()) {
            Entry<T> next = retained.entries.get(current.getKey());
            if (next == null || next.value() != current.getValue().value()) current.getValue().value().close();
        }
    }

    @Override public void close() { entries.values().forEach(entry -> entry.value().close()); }
}
