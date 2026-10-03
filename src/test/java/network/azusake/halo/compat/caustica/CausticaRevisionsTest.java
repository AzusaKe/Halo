package network.azusake.halo.compat.caustica;
import java.util.concurrent.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CausticaRevisionsTest {
 @Test void asynchronousReplacementRejectsLateCompletionAndPreservesLastReadyRevision(){
  var released=new ArrayList<String>();var revisions=new CausticaRevisions<String>(released::add);
  revisions.request("old",CompletableFuture.completedFuture(null));assertEquals("old",revisions.current());
  var slow=new CompletableFuture<Void>();revisions.request("stale",slow);assertEquals("old",revisions.current());
  var latest=new CompletableFuture<Void>();revisions.request("new",latest);assertEquals(List.of("stale"),released);
  slow.complete(null);assertEquals("old",revisions.current());latest.complete(null);assertEquals("new",revisions.current());
  assertEquals(List.of("stale","old"),released);
  var failed=CompletableFuture.failedFuture(new IllegalStateException("build failed"));revisions.request("failed",failed);assertEquals("new",revisions.current());
  revisions.close();revisions.close();assertEquals(List.of("stale","old","failed","new"),released);
 }
 @Test void worldTeardownInvalidatesPendingAndLaterRequests(){
  var released=new ArrayList<String>();var revisions=new CausticaRevisions<String>(released::add);var late=new CompletableFuture<Void>();
  revisions.request("pending",late);revisions.close();late.complete(null);assertNull(revisions.current());
  revisions.request("afterStop",CompletableFuture.completedFuture(null));assertEquals(List.of("pending","afterStop"),released);
 }
}
