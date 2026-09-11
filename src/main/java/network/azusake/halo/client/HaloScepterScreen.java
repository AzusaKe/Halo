package network.azusake.halo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

    private EditBox searchField;
    private HaloListWidget haloList;
    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int missingTargetTicks;
    private boolean closeSent;

    public HaloScepterScreen(int targetEntityId, UUID targetUuid, String targetName) {
        super(Component.translatable("screen.halo.halo_scepter.title"));
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

        searchField = new EditBox(
            font,
            panelLeft + 14,
            panelTop + 43,
            panelWidth - 28,
            20,
            Component.translatable("screen.halo.halo_scepter.search")
        );
        searchField.setMaxLength(128);
        searchField.setHint(Component.translatable("screen.halo.halo_scepter.search_hint"));
        searchField.setResponder(this::rebuildList);
        addRenderableWidget(searchField);

        int listTop = panelTop + 70;
        int listBottom = panelBottom - 34;
        haloList = new HaloListWidget(minecraft, panelWidth - 28, height, listTop, listBottom, ROW_HEIGHT);
        haloList.setLeftPos(panelLeft + 14);
        haloList.setRenderBackground(false);
        haloList.setRenderTopAndBottom(false);
        addRenderableWidget(haloList);

        addRenderableWidget(Button.builder(
            Component.translatable("gui.halo.close"),
            button -> onClose()
        ).bounds(width / 2 - 50, panelBottom - 27, 100, 20).build());

        rebuildList("");
        setInitialFocus(searchField);
        searchField.setFocused(true);
    }

    private void rebuildList(String query) {
        if (haloList == null) {
            return;
        }
        List<ResourceLocation> matches = HaloIdMatcher.filterAndSort(
            HaloJsonLoader.getDefinitions().keySet(), query
        );
        haloList.setIdentifiers(matches);
    }

    private void choose(ResourceLocation definitionId) {
        HaloNetworkClient.sendScepterSelection(definitionId);
    }

    @Override
    public void tick() {
        super.tick();
        searchField.tick();

        Entity target = minecraft == null || minecraft.level == null
            ? null
            : minecraft.level.getEntity(targetEntityId);
        boolean valid = target instanceof LivingEntity living
            && living.isAlive()
            && targetUuid.equals(target.getUUID());
        if (valid) {
            missingTargetTicks = 0;
        } else if (++missingTargetTicks > 10) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                    Component.translatable("message.halo.halo_scepter.invalid_target"), true
                );
            }
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE0101520);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF4F6A78);
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF090C10);

        graphics.drawCenteredString(font, title, width / 2, panelTop + 10, 0x7FE8FF);
        graphics.drawCenteredString(
            font,
            Component.translatable("screen.halo.halo_scepter.target", targetName),
            width / 2,
            panelTop + 26,
            0xD5DDE5
        );

        super.render(graphics, mouseX, mouseY, delta);

        if (haloList.children().isEmpty()) {
            Component empty = HaloJsonLoader.getDefinitions().isEmpty()
                ? Component.translatable("screen.halo.halo_scepter.no_definitions")
                : Component.translatable("screen.halo.halo_scepter.no_results");
            graphics.drawCenteredString(
                font,
                empty,
                width / 2,
                (panelTop + panelBottom) / 2,
                0x8B98A5
            );
        }
    }

    @Override
    public void onClose() {
        sendCloseOnce();
        super.onClose();
    }

    @Override
    public void removed() {
        sendCloseOnce();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendCloseOnce() {
        if (!closeSent) {
            closeSent = true;
            HaloNetworkClient.sendScepterClose();
        }
    }

    private final class HaloListWidget extends ObjectSelectionList<HaloEntry> {

        private HaloListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
        }

        private void setIdentifiers(List<ResourceLocation> identifiers) {
            clearEntries();
            for (ResourceLocation identifier : identifiers) {
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
        protected int getScrollbarPosition() {
            return getRowRight() + 3;
        }
    }

    private final class HaloEntry extends ObjectSelectionList.Entry<HaloEntry> {

        private final ResourceLocation identifier;

        private HaloEntry(ResourceLocation identifier) {
            this.identifier = identifier;
        }

        @Override
        public Component getNarration() {
            return Component.literal(identifier.toString());
        }

        @Override
        public void render(
            GuiGraphics graphics,
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
            graphics.fill(x, y, x + entryWidth, y + entryHeight - 2, color);
            graphics.drawString(
                font,
                identifier.toString(),
                x + 7,
                y + (entryHeight - font.lineHeight) / 2 - 1,
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
