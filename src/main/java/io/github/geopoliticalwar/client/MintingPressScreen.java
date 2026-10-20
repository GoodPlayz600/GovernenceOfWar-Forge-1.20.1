package io.github.geopoliticalwar.client;

import io.github.geopoliticalwar.menu.MintingPressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MintingPressScreen extends AbstractContainerScreen<MintingPressMenu> {
    public MintingPressScreen(MintingPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Start"), button -> {
            if (minecraft != null && minecraft.gameMode != null && menu != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 110, topPos + 30, 55, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A2A2A);
        graphics.fill(leftPos + 7, topPos + 7, leftPos + imageWidth - 7, topPos + 75, 0xFF454545);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFFFFF, false);
        graphics.drawString(font, "Progress: " + menu.progress(), 8, 58, 0xFFFFFF, false);
        graphics.drawString(font, menu.active() ? "Active" : "Ready", 8, 68, 0xFFFFFF, false);
    }
}
