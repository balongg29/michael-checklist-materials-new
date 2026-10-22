package com.farmlist;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Right-click a finalized Checklist Book to open its checklist.
 * Only books carrying the Checklist Book model are affected, so ordinary books work as normal.
 */
public final class BookInteraction {

    static final Identifier CHECKLIST_BOOK_MODEL =
            Identifier.fromNamespaceAndPath("farmlist", "checklist_book");

    private BookInteraction() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!world.isClientSide()) return InteractionResult.PASS;
            if (!isChecklistBook(stack)) return InteractionResult.PASS;

            String title = bookTitle(stack);
            Minecraft.getInstance().gui.setScreen(new ChecklistScreen(title));
            return InteractionResult.SUCCESS;
        });
    }

    static boolean isChecklistBook(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) return false;
        return CHECKLIST_BOOK_MODEL.equals(stack.get(DataComponents.ITEM_MODEL));
    }

    /** The title written on the cover, which matches the transcription name. */
    static String bookTitle(ItemStack stack) {
        var content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content == null ? null : content.title().raw();
    }
}
