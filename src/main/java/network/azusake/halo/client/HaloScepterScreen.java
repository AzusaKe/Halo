package network.azusake.halo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import network.azusake.halo.json.HaloJsonLoader;
import network.azusake.halo.network.HaloNetworkClient;
import network.azusake.halo.util.HaloIdMatcher;

import java.util.List;
import java.util.UUID;

/** Client-only searchable selector for a server-locked halo-scepter target. */
public final class HaloScepterScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_MARGIN = 18;
    private static final int ROW_HEIGHT = 22;

    private final int targetEntityId;
    private final UUID targetUuid;
    private final String targetName;

    private TextFieldWidget searchField;
    private HaloListWidget haloList;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int missingTargetTicks;
    private boolean closeSent;

    public HaloScepterScreen(int targetEntityId, UUID targetUuid, String targetName) {
        super(Text.translatable("screen.halo.halo_scepter.title"));
        this.targetEntityId = targetEntityId;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(PANEL_WIDTH, width - PANEL_MARGIN * 2);
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        panelTop = PANEL_MARGIN;
        panelBottom = height - PANEL_MARGIN;

        searchField = new TextFieldWidget(
            textRenderer,
            panelLeft + 14,
            panelTop + 43,
            panelWidth - 28,
            20,
            Text.translatable("screen.halo.halo_scepter.search")
        );
        searchField.setMaxLength(128);
        searchField.setPlaceholder(Text.translatable("screen.halo.halo_scepter.search_hint"));
        searchField.setChangedListener(this::rebuildList);
        addDrawableChild(searchField);

        int listTop = panelTop + 70;
        int listBottom = panelBottom - 34;
        haloList = new HaloListWidget(client, panelWidth - 28, height, listTop, listBottom, ROW_HEIGHT);
        haloList.setLeftPos(panelLeft + 14);
        haloList.setRenderBackground(false);
        haloList.setRenderHorizontalShadows(false);
        addDrawableChild(haloList);

        addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.halo.close"),
            button -> close()
        ).dimensions(width / 2 - 50, panelBottom - 27, 100, 20).build());

        rebuildList("");
        setInitialFocus(searchField);
        searchField.setFocused(true);
    }

    private void rebuildList(String query) {
        if (haloList == null) {
            return;
        }
        List<Identifier> matches = HaloIdMatcher.filterAndSort(
            HaloJsonLoader.getDefinitions().keySet(), query
        );
        haloList.setIdentifiers(matches);
    }

    private void choose(Identifier definitionId) {
        HaloNetworkClient.sendScepterSelection(definitionId);
    }

    @Override
    public void tick() {
        super.tick();
        searchField.tick();

        Entity target = client == null || client.world == null
            ? null
            : client.world.getEntityById(targetEntityId);
        boolean valid = target instanceof LivingEntity living
            && living.isAlive()
            && targetUuid.equals(target.getUuid());
        if (valid) {
            missingTargetTicks = 0;
        } else if (++missingTargetTicks > 10) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.translatable("message.halo.halo_scepter.invalid_target"), true
                );
            }
            close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE0101520);
        context.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF4F6A78);
        context.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF090C10);

        context.drawCenteredTextWithShadow(
            textRenderer, title, width / 2, panelTop + 10, 0x7FE8FF
        );
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("screen.halo.halo_scepter.target", targetName),
            width / 2,
            panelTop + 26,
            0xD5DDE5
        );

        super.render(context, mouseX, mouseY, delta);

        if (haloList.children().isEmpty()) {
            Text empty = HaloJsonLoader.getDefinitions().isEmpty()
                ? Text.translatable("screen.halo.halo_scepter.no_definitions")
                : Text.translatable("screen.halo.halo_scepter.no_results");
            context.drawCenteredTextWithShadow(
                textRenderer,
                empty,
                width / 2,
                (panelTop + panelBottom) / 2,
                0x8B98A5
            );
        }
    }

    @Override
    public void close() {
        sendCloseOnce();
        super.close();
    }

    @Override
    public void removed() {
        sendCloseOnce();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendCloseOnce() {
        if (!closeSent) {
            closeSent = true;
            HaloNetworkClient.sendScepterClose();
        }
    }

    private final class HaloListWidget extends AlwaysSelectedEntryListWidget<HaloEntry> {

        private HaloListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        private void setIdentifiers(List<Identifier> identifiers) {
            clearEntries();
            for (Identifier identifier : identifiers) {
                addEntry(new HaloEntry(identifier));
            }
            setScrollAmount(0.0);
            setSelected(null);
        }

        @Override
        public int getRowWidth() {
            return width - 14;
        }

        @Override
        protected int getScrollbarPositionX() {
            return getRowRight() + 3;
        }
    }

    private final class HaloEntry extends AlwaysSelectedEntryListWidget.Entry<HaloEntry> {

        private final Identifier identifier;

        private HaloEntry(Identifier identifier) {
            this.identifier = identifier;
        }

        @Override
        public Text getNarration() {
            return Text.literal(identifier.toString());
        }

        @Override
        public void render(
            DrawContext context,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean hovered,
            float tickDelta
        ) {
            int color = hovered ? 0xA0335668 : (index % 2 == 0 ? 0x70303B46 : 0x5027313A);
            context.fill(x, y, x + entryWidth, y + entryHeight - 2, color);
            context.drawTextWithShadow(
                textRenderer,
                identifier.toString(),
                x + 7,
                y + (entryHeight - textRenderer.fontHeight) / 2 - 1,
                hovered ? 0x9EEBFF : 0xE6EDF3
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            haloList.setSelected(this);
            choose(identifier);
            return true;
        }
    }
}
