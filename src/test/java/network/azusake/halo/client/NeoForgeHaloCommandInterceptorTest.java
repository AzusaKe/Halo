package network.azusake.halo.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeHaloCommandInterceptorTest {
    @Test void selfTargetIsAnUnrestrictedLiteralWithDefinitionSuggestions() throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        new NeoForgeHaloCommandInterceptor().registerCommands(dispatcher, null);

        var halo = dispatcher.getRoot().getChild("halo");
        for (String command : new String[]{"show", "hide", "inspect"}) {
            var self = halo.getChild(command).getChild("@s");
            assertInstanceOf(LiteralCommandNode.class, self);
            assertNotNull(self.getCommand());
            assertEquals(List.of("@s"), self.listSuggestions(null, new SuggestionsBuilder("", 0)).join()
                .getList().stream().map(suggestion -> suggestion.getText()).toList());
        }
        var definition = halo.getChild("show").getChild("@s").getChild("definition");
        assertNotNull(definition);
        assertNotNull(assertInstanceOf(ArgumentCommandNode.class, definition).getCustomSuggestions());

        var parsed = dispatcher.parse("halo show @s halo:ring_default", null);
        assertEquals("", parsed.getReader().getRemaining());
        // Retain the entity argument branch for modded servers, names and privileged selectors.
        assertNotNull(halo.getChild("show").getChild("target"));
    }
}
