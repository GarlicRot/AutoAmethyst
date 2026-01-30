package garlicrot.autoamethyst;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AutoAmethystModule extends ToggleableModule {

    private static AutoAmethystModule instance;
    private final Minecraft mc = Minecraft.getInstance();

    private static final double VANILLA_REACH = 4.5;

    private static final int SCAN_RANGE = 12;

    private static final int FAIL_COOLDOWN_TICKS = 4;

    private static final int VERIFY_AFTER_TICKS = 2;

    private static final int MAX_FAILURE_STREAK = 3;

    private final EnumSetting<BreakStage> breakSetting =
            new EnumSetting<>("Break", "Which growth stage(s) to break.", BreakStage.ALL);

    private final ColorSetting buddingColor =
            new ColorSetting("Budding Color", new Color(170, 60, 255, 60))
                    .setAlphaAllowed(true)
                    .setThemeSyncAllowed(true);

    private final ColorSetting targetColor =
            new ColorSetting("Target Color", new Color(60, 255, 120, 80))
                    .setAlphaAllowed(true)
                    .setThemeSyncAllowed(true);

    private final NumberSetting<Float> lineWidth =
            new NumberSetting<>("Line Width", "Outline width.", 1.0f, 0.0f, 5.0f)
                    .incremental(0.25f);

    private final BooleanSetting renderSettings =
            new BooleanSetting("Render", "Render budding + targets.", true);

    private final NumberSetting<Integer> retryCooldownTicks =
            new NumberSetting<>("Retry Cooldown", "Ticks before retrying the same target.", 4, 0, 20)
                    .incremental(1);

    private final BooleanSetting swing =
            new BooleanSetting("Swing", "Swing hand when breaking (visual).", true);

    private final List<BlockPos> buddingCache = new ArrayList<>();
    private final List<BlockPos> targets = new ArrayList<>();

    private final Map<BlockPos, Integer> cooldowns = new HashMap<>();

    private final Map<BlockPos, Integer> failureStreak = new HashMap<>();

    private int tickCounter = 0;

    private BlockPos miningPos = null;
    private Direction miningDir = null;
    private int miningStartTick = 0;

    private BlockPos verifyPos = null;
    private int verifyTick = 0;

    private AutoAmethystModule() {
        super(
                "AutoAmethyst",
                "Automatically breaks amethyst buds/clusters without breaking Budding Amethyst.",
                ModuleCategory.MISC
        );

        renderSettings.addSubSettings(
                buddingColor,
                targetColor,
                lineWidth
        );

        this.registerSettings(
                breakSetting,
                renderSettings,
                retryCooldownTicks,
                swing
        );
    }

    public static synchronized AutoAmethystModule getInstance() {
        if (instance == null) instance = new AutoAmethystModule();
        return instance;
    }

    @Override
    public void onDisable() {
        buddingCache.clear();
        targets.clear();
        cooldowns.clear();
        failureStreak.clear();
        abortMining();
        verifyPos = null;
    }

    @Subscribe(stage = Stage.PRE)
    public void preTick(EventUpdate event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;

        tickCounter++;

        if (!cooldowns.isEmpty()) {
            cooldowns.entrySet().removeIf(e -> (tickCounter - e.getValue()) >= 400);
        }
        if (!failureStreak.isEmpty()) {
            failureStreak.entrySet().removeIf(e -> (tickCounter - cooldowns.getOrDefault(e.getKey(), tickCounter)) >= 600);
        }

        if (verifyPos != null) {
            if ((tickCounter - verifyTick) >= VERIFY_AFTER_TICKS) {
                BlockState now = mc.level.getBlockState(verifyPos);

                boolean success = now.isAir() || !isShardDropper(now);

                if (success) {
                    failureStreak.remove(verifyPos);
                } else {

                    int streak = failureStreak.getOrDefault(verifyPos, 0) + 1;
                    failureStreak.put(verifyPos, streak);

                    int baseCd = Math.max(retryCooldownTicks.getValue(), FAIL_COOLDOWN_TICKS);

                    int extra = Math.min(60, (streak * streak) + (streak * 2));
                    @SuppressWarnings("unused")
                    int backoff = baseCd + extra;

                    cooldowns.put(verifyPos, tickCounter);

                    if (streak >= MAX_FAILURE_STREAK) {
                        cooldowns.put(verifyPos, tickCounter - (baseCd - 80));
                    }
                }

                verifyPos = null;
            }

            return;
        }

        if (miningPos != null) {
            BlockState st = mc.level.getBlockState(miningPos);

            if (st.isAir() || !isShardDropper(st) || !matchesStageFilter(st, breakSetting.getValue())) {
                miningPos = null;
                miningDir = null;
                return;
            }

            if ((tickCounter - miningStartTick) >= 1) {
                sendDig(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, miningPos, miningDir);

                if (swing.getValue()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }

                verifyPos = miningPos;
                verifyTick = tickCounter;

                cooldowns.put(miningPos, tickCounter);

                miningPos = null;
                miningDir = null;
            }

            return;
        }

        buddingCache.clear();
        targets.clear();

        final BlockPos playerPos = mc.player.blockPosition();
        final int r = SCAN_RANGE;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    mpos.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);

                    BlockState st = mc.level.getBlockState(mpos);
                    if (st.getBlock() != Blocks.BUDDING_AMETHYST) continue;

                    BlockPos buddingPos = mpos.immutable();
                    buddingCache.add(buddingPos);

                    for (Direction attachSide : Direction.values()) {
                        BlockPos shardPos = buddingPos.relative(attachSide);
                        BlockState shardState = mc.level.getBlockState(shardPos);

                        if (!isShardDropper(shardState)) continue;

                        if (shardState.getBlock() instanceof AmethystClusterBlock) {
                            Direction facingOutward = shardState.getValue(AmethystClusterBlock.FACING);
                            if (facingOutward != attachSide) continue;
                        }

                        if (!matchesStageFilter(shardState, breakSetting.getValue())) continue;

                        if (!isWithinVanillaReach(shardPos)) continue;

                        targets.add(shardPos.immutable());
                    }
                }
            }
        }

        if (targets.isEmpty()) return;

        targets.sort(Comparator.comparingDouble(this::eyeDistanceSqrToCenter));

        for (BlockPos pos : targets) {
            int baseCd = Math.max(retryCooldownTicks.getValue(), FAIL_COOLDOWN_TICKS);
            Integer last = cooldowns.get(pos);
            if (last != null && (tickCounter - last) < baseCd) continue;

            int streak = failureStreak.getOrDefault(pos, 0);
            if (streak > 0) {
                int extra = Math.min(60, (streak * streak) + (streak * 2));
                if (last != null && (tickCounter - last) < (baseCd + extra)) continue;
            }

            BlockState state = mc.level.getBlockState(pos);

            if (!isShardDropper(state)) {
                cooldowns.put(pos, tickCounter);
                continue;
            }

            if (!matchesStageFilter(state, breakSetting.getValue())) {
                cooldowns.put(pos, tickCounter);
                continue;
            }

            Direction dir = getBestDigFace(pos, state);

            sendDig(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, dir);

            sendDig(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, dir);

            miningPos = pos;
            miningDir = dir;
            miningStartTick = tickCounter;

            break;
        }
    }

    private void sendDig(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction dir) {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.send(new ServerboundPlayerActionPacket(action, pos, dir));
    }

    private void abortMining() {
        if (miningPos != null && miningDir != null) {
            sendDig(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, miningPos, miningDir);
        }
        miningPos = null;
        miningDir = null;
    }

    private boolean isShardDropper(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.BUDDING_AMETHYST) return false;
        if (b == Blocks.AMETHYST_BLOCK) return false;
        return b instanceof AmethystClusterBlock;
    }

    private boolean matchesStageFilter(BlockState state, BreakStage filter) {
        Block b = state.getBlock();

        boolean isSmall = b == Blocks.SMALL_AMETHYST_BUD;
        boolean isMedium = b == Blocks.MEDIUM_AMETHYST_BUD;
        boolean isLarge = b == Blocks.LARGE_AMETHYST_BUD;
        boolean isCluster = b == Blocks.AMETHYST_CLUSTER;

        if (!(isSmall || isMedium || isLarge || isCluster)) return false;

        return switch (filter) {
            case SMALL_ONLY -> isSmall;
            case MEDIUM_ONLY -> isMedium;
            case LARGE_ONLY -> isLarge;
            case CLUSTER_ONLY -> isCluster;
            case SMALL_TO_LARGE -> (isSmall || isMedium || isLarge);
            case LARGE_AND_CLUSTER -> (isLarge || isCluster);
            case ALL -> true;
        };
    }

    private boolean isWithinVanillaReach(BlockPos pos) {
        return eyeDistanceSqrToCenter(pos) <= (VANILLA_REACH * VANILLA_REACH);
    }

    private double eyeDistanceSqrToCenter(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        Vec3 eye = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Vec3 center = Vec3.atCenterOf(pos);
        return eye.distanceToSqr(center);
    }

    private Direction getBestDigFace(BlockPos pos, BlockState state) {
        Direction rayDir = getRaycastFace(pos);
        if (rayDir != null) return rayDir;

        if (state.getBlock() instanceof AmethystClusterBlock) {
            return state.getValue(AmethystClusterBlock.FACING);
        }

        return getDirectionForBreak(pos);
    }

    private Direction getRaycastFace(BlockPos pos) {
        if (mc.player == null || mc.level == null) return null;

        Vec3 eye = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Vec3 target = Vec3.atCenterOf(pos);

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) return null;
        if (!hit.getBlockPos().equals(pos)) return null;

        return hit.getDirection();
    }

    private Direction getDirectionForBreak(BlockPos pos) {
        if (mc.player == null) return Direction.UP;

        Vec3 eye = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 d = center.subtract(eye);

        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);

        if (ax >= ay && ax >= az) return d.x > 0 ? Direction.WEST : Direction.EAST;
        if (ay >= ax && ay >= az) return d.y > 0 ? Direction.DOWN : Direction.UP;
        return d.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (!renderSettings.getValue()) return;
        if (mc.player == null || mc.level == null) return;

        IRenderer3D r = event.getRenderer();
        r.begin(event.getMatrixStack());
        r.setLineWidth(lineWidth.getValue());

        for (BlockPos budding : buddingCache) {
            r.drawBox(
                    budding,
                    true,
                    true,
                    ColorUtils.transparency(buddingColor.getValueRGB(), buddingColor.getAlpha())
            );
        }

        for (BlockPos target : targets) {
            r.drawBox(
                    target,
                    true,
                    true,
                    ColorUtils.transparency(targetColor.getValueRGB(), targetColor.getAlpha())
            );
        }

        r.end();
    }

    public enum BreakStage {
        CLUSTER_ONLY,
        LARGE_ONLY,
        MEDIUM_ONLY,
        SMALL_ONLY,
        SMALL_TO_LARGE,
        LARGE_AND_CLUSTER,
        ALL
    }
}
