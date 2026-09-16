package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RendererCommandTreeTest {
    private static List<String> suggestions(CommandDispatcher<Object> tree, String input) {
        return tree.getCompletionSuggestions(tree.parse(input, new Object())).join()
            .getList().stream().map(s -> s.getText()).toList();
    }

    @Test void existingServerRootKeepsItsCommandsAndGainsRendererDescendants() throws Exception {
        var tree = new CommandDispatcher<Object>();
        var root = tree.register(LiteralArgumentBuilder.<Object>literal("halo").executes(ctx -> 42)
            .then(LiteralArgumentBuilder.<Object>literal("reload").executes(ctx -> 43)));
        RendererCommandTree.addSuggestions(tree);
        RendererCommandTree.addSuggestions(tree);
        assertSame(root, tree.getRoot().getChild("halo"));
        assertEquals(42, tree.execute("halo", new Object()));
        assertEquals(43, tree.execute("halo reload", new Object()));
        assertEquals(List.of("reload", "renderer"), suggestions(tree, "halo re"));
        assertEquals(List.of("cached", "compatibility"), suggestions(tree, "halo renderer "));
        assertEquals(List.of("cached"), suggestions(tree, "halo renderer ca"));
    }

    @Test void vanillaServerAndRefreshedTreesReceiveUnrestrictedSuggestions() {
        for (int refresh = 0; refresh < 2; refresh++) {
            var tree = new CommandDispatcher<Object>();
            RendererCommandTree.addSuggestions(tree);
            assertNull(tree.getRoot().getChild("halo"), "Do not preempt Fabric's complete client root");
            tree.register(LiteralArgumentBuilder.<Object>literal("halo")
                .then(LiteralArgumentBuilder.<Object>literal("list"))
                .then(RendererCommandTree.command((source, backend) -> 0)));
            RendererCommandTree.addSuggestions(tree);
            assertEquals(List.of("list", "renderer"), suggestions(tree, "halo "));
            assertEquals(List.of("cached", "compatibility"), suggestions(tree, "halo renderer "));
        }
    }

    @Test void executionTreeUsesSameLiteralsAndSelectsLocally() throws Exception {
        var tree = new CommandDispatcher<Object>();
        var selected = new AtomicReference<String>();
        tree.register(LiteralArgumentBuilder.<Object>literal("halo")
            .then(RendererCommandTree.command((source, backend) -> { selected.set(backend); return 1; })));
        assertEquals(1, tree.execute("halo renderer cached", new Object()));
        assertEquals("cached", selected.get());
        assertEquals(1, tree.execute("halo renderer compatibility", new Object()));
        assertEquals("compatibility", selected.get());
        assertEquals(1, tree.execute("halo renderer", new Object()));
        assertNull(selected.get());
    }
}
