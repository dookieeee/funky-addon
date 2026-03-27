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
import net.minecraft.block.Blocks;

import java.util.Set;

public class AutoDiamondBot extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {
        WOOD, STONE, IRON, SMELT, IRON_GEAR, DIAMOND, DIAMOND_GEAR, DONE
    }

    private Stage stage;
    private BlockPos craftingPos;
    private BlockPos furnacePos;

    // =========================
    // KEEP ITEMS
    // =========================

    private static final Set<Item> KEEP = Set.of(
        Items.DIAMOND,
        Items.DIAMOND_PICKAXE,
        Items.DIAMOND_AXE,
        Items.DIAMOND_SWORD,
        Items.DIAMOND_SHOVEL,
        Items.DIAMOND_HELMET,
        Items.DIAMOND_CHESTPLATE,
        Items.DIAMOND_LEGGINGS,
        Items.DIAMOND_BOOTS,

        Items.IRON_INGOT,
        Items.IRON_PICKAXE,
        Items.IRON_AXE,
        Items.IRON_SWORD,
        Items.IRON_SHOVEL,

        Items.CRAFTING_TABLE,
        Items.FURNACE,
        Items.COAL,
        Items.COBBLESTONE
    );

    public AutoDiamondBot() {
        super(com.example.addon.AddonTemplate.CATEGORY, "auto-diamond", "Full diamond automation bot.");
    }

    @Override
    public void onActivate() {
        stage = Stage.WOOD;
        craftingPos = null;
        furnacePos = null;

        warning("⚠ Give food + enable AutoEat module!");
    }

    // =========================
    // MAIN LOOP
    // =========================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        manageInventory();

        switch (stage) {
            case WOOD -> {
                if (count(Items.OAK_LOG) < 12) mine("log");
                else stage = Stage.STONE;
            }

            case STONE -> {
                if (!has(Items.WOODEN_PICKAXE)) craft(Items.WOODEN_PICKAXE);
                else if (count(Items.COBBLESTONE) < 25) mine("stone");
                else stage = Stage.IRON;
            }

            case IRON -> {
                if (!has(Items.STONE_PICKAXE)) craft(Items.STONE_PICKAXE);
                else if (count(Items.RAW_IRON) < 40) mine("iron_ore");
                else stage = Stage.SMELT;
            }

            case SMELT -> {
                if (!has(Items.FURNACE)) {
                    craft(Items.FURNACE);
                    return;
                }

                if (count(Items.COAL) < 8) {
                    mine("coal_ore");
                    return;
                }

                smelt();

                if (count(Items.IRON_INGOT) >= 40) stage = Stage.IRON_GEAR;
            }

            case IRON_GEAR -> {
                craft(Items.IRON_PICKAXE);
                craft(Items.IRON_AXE);
                craft(Items.IRON_SWORD);
                craft(Items.IRON_SHOVEL);

                craft(Items.IRON_HELMET);
                craft(Items.IRON_CHESTPLATE);
                craft(Items.IRON_LEGGINGS);
                craft(Items.IRON_BOOTS);

                if (hasFullIron()) stage = Stage.DIAMOND;
            }

            case DIAMOND -> {
                if (count(Items.DIAMOND) < 35) mine("diamond_ore");
                else stage = Stage.DIAMOND_GEAR;
            }

            case DIAMOND_GEAR -> {
                craft(Items.DIAMOND_PICKAXE);
                craft(Items.DIAMOND_AXE);
                craft(Items.DIAMOND_SWORD);
                craft(Items.DIAMOND_SHOVEL);

                craft(Items.DIAMOND_HELMET);
                craft(Items.DIAMOND_CHESTPLATE);
                craft(Items.DIAMOND_LEGGINGS);
                craft(Items.DIAMOND_BOOTS);

                if (hasFullDiamond()) stage = Stage.DONE;
            }

            case DONE -> {
                info("Full diamond achieved.");
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
    // INVENTORY MANAGEMENT
    // =========================

    private void manageInventory() {
        int freeSlots = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) {
                freeSlots++;
                continue;
            }

            if (!KEEP.contains(stack.getItem()) && freeSlots < 5) {
                drop(i);
            }
        }
    }

    private void drop(int slot) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot,
            1,
            SlotActionType.THROW,
            mc.player
        );
    }

    // =========================
    // CRAFTING
    // =========================

    private void craft(Item item) {
        if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)) {
            openCrafting();
            return;
        }

        // Simplified crafting trigger
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            0,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
    }

    // =========================
    // FURNACE
    // =========================

    private void smelt() {
        if (!(mc.player.currentScreenHandler instanceof FurnaceScreenHandler handler)) {
            openFurnace();
            return;
        }

        move(handler, Items.RAW_IRON, 0);
        move(handler, Items.COAL, 1);

        mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    // =========================
    // BLOCKS
    // =========================

    private void openCrafting() {
        if (craftingPos == null) craftingPos = find(Blocks.CRAFTING_TABLE);

        if (craftingPos == null) {
            craftingPos = place();
        }

        walkTo(craftingPos);

        if (near(craftingPos)) interact(craftingPos);
    }

    private void openFurnace() {
        if (furnacePos == null) furnacePos = find(Blocks.FURNACE);

        if (furnacePos == null) {
            furnacePos = place();
        }

        walkTo(furnacePos);

        if (near(furnacePos)) interact(furnacePos);
    }

    private BlockPos find(net.minecraft.block.Block block) {
        BlockPos base = mc.player.getBlockPos();

        for (int x = -5; x <= 5; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos p = base.add(x, y, z);
                    if (mc.world.getBlockState(p).getBlock() == block) return p;
                }
            }
        }
        return null;
    }

    private BlockPos place() {
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

    private boolean near(BlockPos pos) {
        return mc.player.getBlockPos().isWithinDistance(pos, 3);
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

    private boolean hasFullDiamond() {
        return has(Items.DIAMOND_HELMET)
            && has(Items.DIAMOND_CHESTPLATE)
            && has(Items.DIAMOND_LEGGINGS)
            && has(Items.DIAMOND_BOOTS)
            && has(Items.DIAMOND_PICKAXE);
    }

    private void move(FurnaceScreenHandler h, Item item, int slot) {
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
