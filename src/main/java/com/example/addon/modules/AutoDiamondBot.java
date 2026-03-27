package com.example.addon.modules;

import baritone.api.BaritoneAPI;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.List;

public class AutoDiamondBot extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // =========================
    // SETTINGS
    // =========================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> foodBlacklist = sgGeneral.add(
        new ItemListSetting.Builder()
            .name("food-blacklist")
            .description("Food items the bot will NOT eat.")
            .defaultValue(
                Items.ROTTEN_FLESH,
                Items.POISONOUS_POTATO,
                Items.CHICKEN,
                Items.SUSPICIOUS_STEW,
                Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE
            )
            .build()
    );

    // =========================

    private enum Stage {
        WOOD, STONE, IRON, SMELT, GEAR, DIAMOND, DONE
    }

    private Stage stage;
    private BlockPos craftingPos;
    private BlockPos furnacePos;

    public AutoDiamondBot() {
        super(com.example.addon.AddonTemplate.CATEGORY, "auto-bot", "Autonomous diamond bot.");
    }

    @Override
    public void onActivate() {
        stage = Stage.WOOD;
        craftingPos = null;
        furnacePos = null;

        warning("⚠ Give the bot safe food before enabling!");
    }

    // =========================
    // MAIN LOOP
    // =========================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        eatIfNeeded();

        switch (stage) {
            case WOOD -> {
                if (count(Items.OAK_LOG) < 10) mine("log");
                else stage = Stage.STONE;
            }

            case STONE -> {
                if (!has(Items.WOODEN_PICKAXE)) craftSimple(Items.WOODEN_PICKAXE);
                else if (count(Items.COBBLESTONE) < 20) mine("stone");
                else stage = Stage.IRON;
            }

            case IRON -> {
                if (!has(Items.STONE_PICKAXE)) craftSimple(Items.STONE_PICKAXE);
                else if (count(Items.RAW_IRON) < 27) mine("iron_ore");
                else stage = Stage.SMELT;
            }

            case SMELT -> {
                if (!has(Items.FURNACE)) {
                    craftSimple(Items.FURNACE);
                    return;
                }

                // ensure fuel
                if (count(Items.COAL) < 5) {
                    mine("coal_ore");
                    return;
                }

                smeltIron();

                if (count(Items.IRON_INGOT) >= 27) {
                    stage = Stage.GEAR;
                }
            }

            case GEAR -> {
                craftSimple(Items.IRON_PICKAXE);
                craftSimple(Items.IRON_HELMET);
                craftSimple(Items.IRON_CHESTPLATE);
                craftSimple(Items.IRON_LEGGINGS);
                craftSimple(Items.IRON_BOOTS);

                if (hasFullIron()) stage = Stage.DIAMOND;
            }

            case DIAMOND -> {
                if (count(Items.DIAMOND) < 24) mine("diamond_ore");
                else stage = Stage.DONE;
            }

            case DONE -> {
                info("Finished.");
                toggle();
            }
        }
    }

    // =========================
    // BARITONE
    // =========================

    private void mine(String block) {
        BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getCommandManager()
            .execute("mine " + block);
    }

    private void walkTo(BlockPos pos) {
        BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getCommandManager()
            .execute("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    // =========================
    // SMART BLOCK DETECTION
    // =========================

    private BlockPos findNearby(Block block, int radius) {
        BlockPos base = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = base.add(x, y, z);

                    if (mc.world.getBlockState(pos).getBlock() == block) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    // =========================
    // CRAFTING (SIMPLIFIED SAFE)
    // =========================

    private void craftSimple(Item item) {
        if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)) {
            openCrafting();
            return;
        }

        // Use recipe book quick craft (much more stable)
        mc.interactionManager.clickRecipe(
            mc.player.currentScreenHandler.syncId,
            mc.world.getRecipeManager().getFirstMatch(
                net.minecraft.recipe.RecipeType.CRAFTING,
                new net.minecraft.inventory.CraftingInventory(
                    mc.player.currentScreenHandler, 3, 3
                ),
                mc.world
            ).orElseThrow(),
            false
        );
    }

    // =========================
    // FURNACE
    // =========================

    private void smeltIron() {
        if (!(mc.player.currentScreenHandler instanceof FurnaceScreenHandler handler)) {
            openFurnace();
            return;
        }

        moveToSlot(handler, Items.RAW_IRON, 0);
        moveToSlot(handler, Items.COAL, 1);

        mc.interactionManager.clickSlot(
            handler.syncId,
            2,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
    }

    // =========================
    // BLOCK INTERACTION
    // =========================

    private void openCrafting() {
        BlockPos nearby = findNearby(Blocks.CRAFTING_TABLE, 5);
        if (nearby != null) craftingPos = nearby;

        if (craftingPos == null) {
            if (!has(Items.CRAFTING_TABLE)) return;
            craftingPos = placeBlock();
        }

        walkTo(craftingPos);

        if (mc.player.getBlockPos().isWithinDistance(craftingPos, 3)) {
            interact(craftingPos);
        }
    }

    private void openFurnace() {
        BlockPos nearby = findNearby(Blocks.FURNACE, 5);
        if (nearby != null) furnacePos = nearby;

        if (furnacePos == null) {
            furnacePos = placeBlock();
        }

        walkTo(furnacePos);

        if (mc.player.getBlockPos().isWithinDistance(furnacePos, 3)) {
            interact(furnacePos);
        }
    }

    private BlockPos placeBlock() {
        BlockPos pos = mc.player.getBlockPos().down();

        mc.interactionManager.interactBlock(
            mc.player,
            mc.player.getActiveHand(),
            new BlockHitResult(mc.player.getEyePos(), Direction.UP, pos, false)
        );

        return pos;
    }

    private void interact(BlockPos pos) {
        mc.interactionManager.interactBlock(
            mc.player,
            mc.player.getActiveHand(),
            new BlockHitResult(mc.player.getEyePos(), Direction.UP, pos, false)
        );
    }

    // =========================
    // FOOD SYSTEM
    // =========================

    private boolean isFood(ItemStack stack) {
        return stack.getItem().getFoodComponent() != null;
    }

    private void eatIfNeeded() {
        if (mc.player.getHungerManager().getFoodLevel() >= 12) return;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (isFood(stack) && !foodBlacklist.get().contains(stack.getItem())) {
                mc.player.getInventory().selectedSlot = i;
                mc.options.useKey.setPressed(true);
                return;
            }
        }
    }

    // =========================
    // HELPERS
    // =========================

    private int count(Item item) {
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) total += s.getCount();
        }
        return total;
    }

    private boolean has(Item item) {
        return count(item) > 0;
    }

    private boolean hasFullIron() {
        return has(Items.IRON_HELMET)
            && has(Items.IRON_CHESTPLATE)
            && has(Items.IRON_LEGGINGS)
            && has(Items.IRON_BOOTS)
            && has(Items.IRON_PICKAXE);
    }

    private void moveToSlot(FurnaceScreenHandler handler, Item item, int slot) {
        int inv = findItem(item);
        if (inv == -1) return;

        click(inv);
        click(slot);
    }

    private int findItem(Item item) {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private void click(int slot) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }
}
