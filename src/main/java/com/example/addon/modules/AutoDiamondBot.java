package com.example.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.MineProcess;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.recipe.Recipe;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;

import java.util.*;

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
        super(com.example.addon.AddonTemplate.CATEGORY, "auto-bot", "Autonomous diamond bot with Baritone.");
    }

    @Override
    public void onActivate() {
        stage = Stage.WOOD;

        warning("⚠ Please give the bot safe food before starting!");
    }

    // =========================
    // MAIN LOOP
    // =========================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        eatIfNeeded();
        cleanInventory();

        switch (stage) {
            case WOOD -> {
                if (count(Items.OAK_LOG) < 10) mine("log");
                else stage = Stage.STONE;
            }

            case STONE -> {
                if (!has(Items.WOODEN_PICKAXE)) craft(Items.WOODEN_PICKAXE);
                else if (count(Items.COBBLESTONE) < 20) mine("stone");
                else stage = Stage.IRON;
            }

            case IRON -> {
                if (!has(Items.STONE_PICKAXE)) craft(Items.STONE_PICKAXE);
                else if (count(Items.RAW_IRON) < 27) mine("iron_ore");
                else stage = Stage.SMELT;
            }

            case SMELT -> {
                if (!has(Items.FURNACE)) {
                    craft(Items.FURNACE);
                } else {
                    smelt();
                    if (count(Items.IRON_INGOT) >= 27) stage = Stage.GEAR;
                }
            }

            case GEAR -> {
                craft(Items.IRON_PICKAXE);
                craft(Items.IRON_HELMET);
                craft(Items.IRON_CHESTPLATE);
                craft(Items.IRON_LEGGINGS);
                craft(Items.IRON_BOOTS);

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

    private void mine(String name) {
        MineProcess mine = BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess();
        mine.mineByName(name);
    }

    private void walkTo(BlockPos pos) {
        BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getCustomGoalProcess()
            .setGoalAndPath(new GoalBlock(pos));
    }

    // =========================
    // CRAFTING
    // =========================

    private void craft(Item item) {
        if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler handler)) {
            openCrafting();
            return;
        }

        Recipe<?> recipe = mc.world.getRecipeManager()
            .values()
            .stream()
            .filter(r -> r.getOutput(mc.world.getRegistryManager()).getItem() == item)
            .findFirst()
            .orElse(null);

        if (recipe == null) return;

        autofill(handler, recipe);

        mc.interactionManager.clickSlot(
            handler.syncId,
            0,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
    }

    private void autofill(CraftingScreenHandler handler, Recipe<?> recipe) {
        int slot = 1;

        for (var ing : recipe.getIngredients()) {
            if (ing.isEmpty()) { slot++; continue; }

            int invSlot = findIngredient(ing);
            if (invSlot == -1) continue;

            click(invSlot);
            click(slot);
            slot++;
        }
    }

    // =========================
    // SMELTING
    // =========================

    private void smelt() {
        if (!(mc.player.currentScreenHandler instanceof FurnaceScreenHandler handler)) {
            openFurnace();
            return;
        }

        moveToSlot(handler, Items.RAW_IRON, 0);

        if (count(Items.COAL) > 0)
            moveToSlot(handler, Items.COAL, 1);
        else
            moveToSlot(handler, Items.OAK_PLANKS, 1);

        mc.interactionManager.clickSlot(
            handler.syncId,
            2,
            0,
            SlotActionType.QUICK_MOVE,
            mc.player
        );
    }

    // =========================
    // BLOCK NAVIGATION (FIXED)
    // =========================

    private void openCrafting() {
        if (!has(Items.CRAFTING_TABLE)) {
            craft(Items.CRAFTING_TABLE);
            return;
        }

        if (craftingPos == null) {
            craftingPos = placeBlock(Items.CRAFTING_TABLE);
        } else {
            walkTo(craftingPos);

            if (mc.player.getBlockPos().isWithinDistance(craftingPos, 3)) {
                interact(craftingPos);
            }
        }
    }

    private void openFurnace() {
        if (furnacePos == null) {
            furnacePos = placeBlock(Items.FURNACE);
        } else {
            walkTo(furnacePos);

            if (mc.player.getBlockPos().isWithinDistance(furnacePos, 3)) {
                interact(furnacePos);
            }
        }
    }

    private BlockPos placeBlock(Item item) {
        BlockPos pos = mc.player.getBlockPos().down();

        mc.interactionManager.interactBlock(
            mc.player,
            mc.world,
            mc.player.getActiveHand(),
            new BlockHitResult(mc.player.getPos(), Direction.UP, pos, false)
        );

        return pos;
    }

    private void interact(BlockPos pos) {
        mc.interactionManager.interactBlock(
            mc.player,
            mc.world,
            mc.player.getActiveHand(),
            new BlockHitResult(mc.player.getPos(), Direction.UP, pos, false)
        );
    }

    // =========================
    // FOOD SYSTEM (FIXED)
    // =========================

    private void eatIfNeeded() {
        if (mc.player.getHungerManager().getFoodLevel() >= 12) return;

        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.isFood() && !foodBlacklist.get().contains(stack.getItem())) {
                mc.player.setCurrentHand(mc.player.getActiveHand());
                mc.options.useKey.setPressed(true);
                return;
            }
        }
    }

    // =========================
    // INVENTORY
    // =========================

    private void cleanInventory() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (stack.isEmpty()) continue;

            if (!(stack.getItem() instanceof ToolItem) &&
                !(stack.getItem() instanceof ArmorItem) &&
                !stack.isFood() &&
                stack.getItem() != Items.DIAMOND &&
                stack.getItem() != Items.IRON_INGOT) {

                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    i,
                    1,
                    SlotActionType.THROW,
                    mc.player
                );
            }
        }
    }

    // =========================
    // HELPERS
    // =========================

    private int count(Item item) {
        int total = 0;
        for (ItemStack s : mc.player.getInventory().main)
            if (s.getItem() == item) total += s.getCount();
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

    private int findIngredient(net.minecraft.recipe.Ingredient ing) {
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            if (ing.test(mc.player.getInventory().main.get(i))) return i;
        }
        return -1;
    }

    private void moveToSlot(FurnaceScreenHandler handler, Item item, int slot) {
        int inv = findItem(item);
        if (inv == -1) return;

        click(inv);
        click(slot);
    }

    private int findItem(Item item) {
        for (int i = 0; i < mc.player.getInventory().main.size(); i++)
            if (mc.player.getInventory().main.get(i).getItem() == item) return i;
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
