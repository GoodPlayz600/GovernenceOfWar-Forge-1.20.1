package io.github.geopoliticalwar.client;

import io.github.geopoliticalwar.menu.EstateBoardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class EstateBoardScreen extends AbstractContainerScreen<EstateBoardMenu> {
    public EstateBoardScreen(EstateBoardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 150;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20252B);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xFF3A424B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var snapshot = menu.snapshot();
        graphics.drawString(font, snapshot.claimName(), 10, 10, 0xFFFFFF, false);
        graphics.drawString(font, "Dimension: " + snapshot.dimension(), 10, 28, 0xD0D0D0, false);
        graphics.drawString(font, "Owner: " + snapshot.owner(), 10, 42, 0xD0D0D0, false);
        graphics.drawString(font, "Listing: " + snapshot.listing(), 10, 56, 0xD0D0D0, false);
        graphics.drawString(font, "Lease: " + snapshot.lease(), 10, 70, 0xD0D0D0, false);
        graphics.drawString(font, "Tax: " + snapshot.tax(), 10, 84, 0xD0D0D0, false);
        graphics.drawString(font, "Occupation: " + snapshot.occupation(), 10, 98, 0xD0D0D0, false);
        graphics.drawString(font, "Captives: " + snapshot.captives(), 10, 112, 0xD0D0D0, false);
        graphics.drawString(font, "Read-only server status", 10, 130, 0xA0E0A0, false);
    }
}
