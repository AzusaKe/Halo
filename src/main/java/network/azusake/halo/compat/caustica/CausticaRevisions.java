package network.azusake.halo.compat.caustica;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Keeps the last usable revision while new asynchronous work runs; stale requests lose publication authority. */
final class CausticaRevisions<T> implements AutoCloseable {
 private final Consumer<T> release;
 private T active,pending;private CompletableFuture<?> ready;private boolean stopped;
 CausticaRevisions(Consumer<T> release){this.release=release;}
 synchronized void request(T value,CompletableFuture<?> readiness){
  if(stopped){release.accept(value);return;}
  if(pending!=null)release.accept(pending);pending=value;ready=readiness;
 }
 synchronized T current(){
  if(pending!=null && ready.isDone() && !ready.isCompletedExceptionally() && !ready.isCancelled()){
   var old=active;active=pending;pending=null;ready=null;if(old!=null)release.accept(old);
  }
  return active;
 }
 public synchronized void close(){if(stopped)return;stopped=true;if(pending!=null)release.accept(pending);if(active!=null)release.accept(active);pending=null;active=null;ready=null;}
}
