package io.github.sethwillemsvg.lotrrevival.client.npc.horn;

import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornMode;
import io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only screens of the command horn and sword; opened from the items' client-side use. */
public final class HornScreens {
    private HornScreens() {}

    public static void openMode(boolean offhand) {
        Minecraft.getInstance().setScreen(new ModeScreen(offhand));
    }

    public static void openRename(boolean offhand, String current) {
        Minecraft.getInstance().setScreen(new RenameScreen(offhand, current));
    }

    /** Picks what a blank horn does. */
    static final class ModeScreen extends Screen {
        private final boolean offhand;

        ModeScreen(boolean offhand) {
            super(Component.translatable("screen.lotrrevival.horn_mode"));
            this.offhand = offhand;
        }

        @Override
        protected void init() {
            HornMode[] choices = {HornMode.HALT, HornMode.READY, HornMode.SUMMON};
            int y = height / 2 - 30;
            for (HornMode mode : choices) {
                addRenderableWidget(Button.builder(Component.translatable(mode.langKey()), button -> {
                            PacketDistributor.sendToServer(new HornPayloads.SetMode(offhand, mode));
                            onClose();
                        })
                        .bounds(width / 2 - 60, y, 120, 20)
                        .build());
                y += 26;
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xFFFFFF);
        }
    }

    /** Edits the squadron of a horn or sword. */
    static final class RenameScreen extends Screen {
        private final boolean offhand;
        private final String current;
        private EditBox box;

        RenameScreen(boolean offhand, String current) {
            super(Component.translatable("screen.lotrrevival.squadron_rename"));
            this.offhand = offhand;
            this.current = current;
        }

        @Override
        protected void init() {
            box = new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, title);
            box.setMaxLength(HiredUnit.SQUADRON_MAX);
            box.setValue(current);
            addRenderableWidget(box);
            setInitialFocus(box);
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
                        PacketDistributor.sendToServer(new HornPayloads.Rename(offhand, box.getValue()));
                        onClose();
                    })
                    .bounds(width / 2 - 100, height / 2 + 16, 98, 20)
                    .build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                    .bounds(width / 2 + 2, height / 2 + 16, 98, 20)
                    .build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
        }
    }
}
