package network.azusake.halo.compat.caustica;

import dev.comfyfluffy.caustica.api.program.*;
import org.slf4j.LoggerFactory;

final class CausticaPrograms implements AutoCloseable {
    interface Geometry { }
    interface Instance { }
    static final ShaderDataType<Geometry> GEOMETRY=ShaderDataType.create("Halo geometry");
    static final ShaderDataType<Instance> INSTANCE=ShaderDataType.create("Halo instance");
    private final ProgramRegistration<SurfaceId<Geometry,Instance>> registration;
    volatile boolean ready;
    final java.util.concurrent.CompletableFuture<Void> readiness=new java.util.concurrent.CompletableFuture<>();
    CausticaPrograms(ProgramChannel channel) {
        var source=ShaderSource.classpath(CausticaPrograms.class,"/assets/halo/shaders/caustica");
        registration=channel.register(builder -> builder.surface(SurfaceDefinition.of(
            source.definition("halo_surface","halo.HaloSurface"),source.definition("halo_coverage","halo.HaloCoverage"),
            ShaderDataType.create("Halo implementation").data(0),GEOMETRY,INSTANCE)));
        registration.whenComplete(result -> {
            if(result instanceof ProgramRegistration.Ready) { ready=true;readiness.complete(null); }
            else if(result instanceof ProgramRegistration.Failed failed) {
                readiness.completeExceptionally(new IllegalStateException(failed.failure().summary()));
                LoggerFactory.getLogger("HaloCaustica").error("Halo program rejected: {}\n{}",failed.failure().summary(),failed.failure().diagnostics());
            }
        });
    }
    SurfaceId<Geometry,Instance> surface() { return registration.exports(); }
    @Override public void close() { ready=false;readiness.cancel(false);registration.close(); }
}
