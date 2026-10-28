package io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.network.AcceptQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.DeclineQuestPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.OfferScreenPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.OpenQuestOfferPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferClosedPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The quest offer screen of a giver: name, opening line, objective, a reward hint for bounties, Accept and Decline.
 * It tells the server when it opens and closes (an open screen pauses the offer's clock) and closes itself when the
 * server says the offer is gone.
 */
public final class OfferScreen extends Screen {
    private static final int PANEL_BG = 0xF0191612;
    private static final int PANEL_BORDER = 0xFF8A7440;
    private static final int GOLD = 0xFFE8C860;
    private static final int TEXT = 0xFFE6E0D0;
    private static final int DIM = 0xFF807868;
    private static final int GREEN = 0xFF80D070;

    private final OpenQuestOfferPayload offer;
    private final Component giverName;
    private final Component opening;
    private final Component objective;
    private final Optional<Component> reward;

    /** Set once a button or the server ended the screen, so {@link #removed()} sends the close notice only once. */
    private boolean finished;

    private List<FormattedCharSequence> openingLines = List.of();
    private List<FormattedCharSequence> objectiveLines = List.of();
    private List<FormattedCharSequence> rewardLines = List.of();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    private OfferScreen(OpenQuestOfferPayload offer, Component giverName, RegistryAccess access) {
        super(Component.translatable("gui.lotrrevival.offer.title"));
        this.offer = offer;
        this.giverName = giverName;
        this.opening = Component.translatable(offer.openingKey());
        this.objective = OfferText.objective(access, offer);
        this.reward = OfferText.rewardHint(access, offer);
    }

    /** Opens (or replaces) the offer screen for a giver. Called by the open payload handler on the client thread. */
    public static void open(OpenQuestOfferPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Component name = mc.level.getEntity(payload.entityId()) instanceof NpcEntity npc
                ? npc.speakerName()
                : Component.translatable("gui.lotrrevival.offer.unknown_giver");
        OfferScreen screen = new OfferScreen(payload, name, mc.level.registryAccess());
        if (mc.screen instanceof OfferScreen previous) {
            // Replaced by a new offer (or the same one again): the server already counts us as a viewer.
            previous.finished = true;
        }
        mc.setScreen(screen);
    }

    /** The server ended the offer: close the screen of that giver, if it is showing, and say why. */
    public static void closeFor(QuestOfferClosedPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof OfferScreen screen && screen.offer.entityId() == payload.entityId()) {
            screen.finished = true;
            mc.setScreen(null);
            mc.gui.getChat().addMessage(Component.translatable(payload.reason().langKey()));
        }
    }

    public int entityId() {
        return offer.entityId();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 8, 260);
        int textWidth = panelWidth - 24;
        openingLines = font.split(opening.copy().withStyle(net.minecraft.ChatFormatting.ITALIC), textWidth);
        objectiveLines = font.split(objective, textWidth);
        rewardLines = reward.map(r -> font.split(r, textWidth)).orElse(List.of());
        int lines = openingLines.size() + objectiveLines.size() + rewardLines.size();
        panelHeight = 24 + lines * 10 + 6 + (rewardLines.isEmpty() ? 0 : 6) + 34;
        panelHeight = Math.min(panelHeight, height - 8);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;

        int buttonY = top + panelHeight - 26;
        int buttonWidth = (panelWidth - 30) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.offer.accept"), b -> accept())
                .bounds(left + 10, buttonY, buttonWidth, 18)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lotrrevival.offer.decline"), b -> decline())
                .bounds(left + 20 + buttonWidth, buttonY, buttonWidth, 18)
                .build());

        PacketDistributor.sendToServer(new OfferScreenPayload(offer.entityId(), true));
    }

    private void accept() {
        PacketDistributor.sendToServer(new AcceptQuestPayload(offer.entityId(), offer.template(), offer.count()));
        end();
    }

    private void decline() {
        PacketDistributor.sendToServer(new DeclineQuestPayload(offer.entityId(), offer.template(), offer.count()));
        end();
    }

    private void end() {
        finished = true;
        onClose();
    }

    @Override
    public void removed() {
        // Escape, or any other close we did not cause ourselves, still has to unpause the offer on the server.
        if (!finished) {
            PacketDistributor.sendToServer(new OfferScreenPayload(offer.entityId(), false));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, PANEL_BORDER);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BG);
        graphics.drawCenteredString(font, giverName, left + panelWidth / 2, top + 8, GOLD);
        int y = top + 24;
        for (FormattedCharSequence line : openingLines) {
            graphics.drawString(font, line, left + 12, y, TEXT, false);
            y += 10;
        }
        y += 6;
        for (FormattedCharSequence line : objectiveLines) {
            graphics.drawString(font, line, left + 12, y, GREEN, false);
            y += 10;
        }
        if (!rewardLines.isEmpty()) {
            y += 6;
            for (FormattedCharSequence line : rewardLines) {
                graphics.drawString(font, line, left + 12, y, DIM, false);
                y += 10;
            }
        }
    }
}
