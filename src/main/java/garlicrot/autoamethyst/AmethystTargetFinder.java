package garlicrot.autoamethyst;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds genuinely reachable, exposed buds/clusters supported by Budding Amethyst. */
public final class AmethystTargetFinder {
    private AmethystTargetFinder() {}

    public record Target(BlockPos pos, Direction face, double distanceSquared) {}
    public record Scan(List<BlockPos> budding, List<Target> targets) {}

    public static Scan scan(ClientLevel level, LocalPlayer player,
                            HarvestRules.HarvestMode mode, int radius, double reach) {
        List<BlockPos> budding = new ArrayList<>();
        List<Target> targets = new ArrayList<>();
        BlockPos origin = player.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BUDDING_AMETHYST)) {
                budding.add(pos.immutable());
                continue;
            }
            if (!isEligible(level, pos, state, mode)) continue;
            Target target = visibleHit(level, player, pos, state, reach);
            if (target != null) targets.add(target);
        }

        targets.sort(Comparator.comparingDouble(Target::distanceSquared));
        return new Scan(List.copyOf(budding), List.copyOf(targets));
    }

    /** Whitelists the four growth stages, never any ordinary or Budding Amethyst block. */
    public static HarvestRules.Stage stageOf(BlockState state) {
        if (state.is(Blocks.SMALL_AMETHYST_BUD)) return HarvestRules.Stage.SMALL;
        if (state.is(Blocks.MEDIUM_AMETHYST_BUD)) return HarvestRules.Stage.MEDIUM;
        if (state.is(Blocks.LARGE_AMETHYST_BUD)) return HarvestRules.Stage.LARGE;
        if (state.is(Blocks.AMETHYST_CLUSTER)) return HarvestRules.Stage.CLUSTER;
        return null;
    }

    public static boolean isEligible(ClientLevel level, BlockPos pos, BlockState state,
                                     HarvestRules.HarvestMode mode) {
        if (!mode.accepts(stageOf(state))) return false;
        if (!(state.getBlock() instanceof AmethystClusterBlock)) return false;

        Direction facing = state.getValue(AmethystClusterBlock.FACING);
        // The cluster's FACING points AWAY from the block it is attached to.
        return level.getBlockState(pos.relative(facing.getOpposite())).is(Blocks.BUDDING_AMETHYST);
    }

    /**
     * Shoot at the real outline shape, not the middle of a full 1x1x1 block.
     * Buds are much smaller than a full cube; a center-of-block ray can miss them.
     * The hit's actual surface is checked against reach, including occlusion.
     */
    public static Target visibleHit(ClientLevel level, LocalPlayer player,
                                    BlockPos pos, BlockState state, double reach) {
        Vec3 eye = player.getEyePosition();
        Target nearest = null;
        for (AABB localBox : state.getShape(level, pos).toAabbs()) {
            Vec3 point = new Vec3(
                    pos.getX() + (localBox.minX + localBox.maxX) * 0.5,
                    pos.getY() + (localBox.minY + localBox.maxY) * 0.5,
                    pos.getZ() + (localBox.minZ + localBox.maxZ) * 0.5
            );
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) continue;
            double distanceSquared = eye.distanceToSqr(hit.getLocation());
            if (distanceSquared > reach * reach) continue;
            if (nearest == null || distanceSquared < nearest.distanceSquared()) {
                nearest = new Target(pos.immutable(), hit.getDirection(), distanceSquared);
            }
        }
        return nearest;
    }
}
