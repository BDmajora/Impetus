package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

// Adds the mod name to item tooltips.
@Mod.EventBusSubscriber(Side.CLIENT)
@SideOnly(Side.CLIENT)
public final class ModNameTooltipHandler {
    // Formatting of the added line.
    private static final String FORMAT = TextFormatting.BLUE.toString() + TextFormatting.ITALIC;

    // Prevents instantiation
    private ModNameTooltipHandler() {
    }

    // Adds the mod name unless the tooltip already ends with it.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!Extras.options().extra.modNameTooltip) {
            return;
        }

        String modName = resolveModName(event.getItemStack());
        if (modName == null || modName.isEmpty()) {
            return;
        }

        List<String> tooltip = event.getToolTip();
        if (!alreadyPresent(tooltip, modName)) {
            tooltip.add(FORMAT + modName);
        }
    }

    // Returns the name of the mod that created the item or null.
    @Nullable
    private static String resolveModName(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        Item item = stack.getItem();
        String modId = item.getCreatorModId(stack);
        if (modId == null) {
            return null;
        }

        ModContainer container = Loader.instance().getIndexedModList().get(modId);
        return container != null ? container.getName() : null;
    }

    // Returns whether the last line of the tooltip is the mod name.
    private static boolean alreadyPresent(List<String> tooltip, String modName) {
        if (tooltip.size() <= 1) {
            return false;
        }

        String last = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(tooltip.size() - 1));
        return modName.equals(last);
    }
}
