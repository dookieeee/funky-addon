package com.example.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public class PortalFinder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private RegistryKey<World> startDimension;
    private boolean goingToPortal = false;

    public PortalFinder() {
        super(AddonTemplate.CATEGORY, "portal-finder", "Finds a SAFE portal and enters it.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        startDimension = mc.world.getRegistryKey();
        BlockPos playerPos = mc.player.getBlockPos();

        BlockPos bestPortal = null;
        double closestDist = Double.MAX_VALUE;

        int radius = 128;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -64; y <= 64; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (mc.world.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
                        if (!isSafe(pos)) continue;

                        double dist = playerPos.getSquaredDistance(pos);

                        if (dist < closestDist) {
                            closestDist = dist;
                            bestPortal = pos;
                        }
                    }
                }
            }
        }

        if (bestPortal == null) {
            error("No safe portal found.");
            toggle();
            return;
        }

        info("Going to safe portal: " + bestPortal);

        goingToPortal = true;

        BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getCustomGoalProcess()
            .setGoalAndPath(new GoalBlock(bestPortal));
    }

    private boolean isSafe(BlockPos pos) {
        // Check block below (must be solid)
        if (mc.world.getBlockState(pos.down()).isAir()) return false;

        // Check dangerous blocks around
        for (BlockPos check : BlockPos.iterate(
            pos.add(-1, -1, -1),
            pos.add(1, 1, 1)
        )) {
            var block = mc.world.getBlockState(check).getBlock();

            if (block == Blocks.LAVA || block == Blocks.FIRE) {
                return false;
            }
        }

        // Avoid big drops (check 3 blocks down)
        for (int i = 1; i <= 3; i++) {
            if (mc.world.getBlockState(pos.down(i)).isAir()) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!goingToPortal || mc.world == null) return;

        if (mc.world.getRegistryKey() != startDimension) {
            info("Dimension changed! Stopping.");

            BaritoneAPI.getProvider()
                .getPrimaryBaritone()
                .getPathingBehavior()
                .cancelEverything();

            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        goingToPortal = false;

        BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getPathingBehavior()
            .cancelEverything();
    }
}
