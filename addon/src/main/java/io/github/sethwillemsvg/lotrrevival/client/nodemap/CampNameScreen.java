package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.nodemap.camp.CampService;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A small name prompt for the Camps tab (make or rename a camp). Done is active for 1-32 characters after trimming; Enter
 * is Done, Escape and Cancel go back to the map. The server checks the name again (CampService), so this only spares
 * the player a refusal.
 */
final class CampNameScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final Consumer<String> onDone;
    private EditBox box;
    private Button done;

    CampNameScreen(Screen parent, Component title, String initial, Consumer<String> onDone) {
        super(title);
        this.parent = parent;
        this.initial = initial;
        this.onDone = onDone;
    }

    static boolean valid(String s) {
        return CampService.cleanName(s) != null;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        String keep = box == null ? initial : box.getValue();
        box = new EditBox(font, cx - 100, cy - 20, 200, 20, title);
        box.setMaxLength(CampService.NAME_MAX + 8);   // room for spaces that trimming removes
        box.setValue(keep);
        addRenderableWidget(box);
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> finish()).bounds(cx - 100, cy + 6, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(cx + 2, cy + 6, 98, 20).build());
        done.active = valid(keep);
        box.setResponder(v -> done.active = valid(v));
        setInitialFocus(box);
    }

    private void finish() {
        String name = CampService.cleanName(box.getValue());
        if (name == null) return;
        onDone.accept(name);
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && done.active) {
            finish();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFFFF);
        String v = box.getValue().trim();
        int colour = valid(box.getValue()) ? 0xFFA0A0A0 : 0xFFFF8080;
        g.drawCenteredString(font, Component.translatable("nodemap.lotr_revival.camps.name_hint", v.length(), CampService.NAME_MAX),
                width / 2, height / 2 + 32, colour);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
