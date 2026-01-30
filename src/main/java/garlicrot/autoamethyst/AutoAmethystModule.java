package garlicrot.autoamethyst;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.utils.ColorUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AutoAmethystModule extends ToggleableModule {

    private static AutoAmethystModule instance;
    private final Minecraft mc = Minecraft.getInstance();

    private static final double VANILLA_REACH = 4.5;

    private final NumberSetting<Integer> range =
            new NumberSetting<>("Range", "Scan radius around you.", 6, 1, 12)
                    .incremental(1);

    private final BooleanSetting requirePickaxe =
            new BooleanSetting("Require Pickaxe", "Only run if you're holding a pickaxe.", true);

    private final NumberSetting<Integer> retryCooldownTicks =
            new NumberSetting<>("Retry Cooldown", "Ticks before retrying the same target.", 4, 0, 20)
                    .incremental(1);

    private final BooleanSetting requireLineOfSight =
            new BooleanSetting("Line of Sight", "Only break if you have clear line-of-sight.", true);

    private final BooleanSetting swing =
            new BooleanSetting("Swing", "Swing hand when breaking (visual).", true);

    private final BooleanSetting render =
            new BooleanSetting("Render", "Render budding + targets.", true);

    private final ColorSetting buddingColor =
            new ColorSetting("Budding Color", new Color(170, 60, 255, 60))
                    .setAlphaAllowed(true)
                    .setThemeSyncAllowed(true);

    private final ColorSetting targetColor =
            new ColorSetting("Target Color", new Color(60, 255, 120, 80))
                    .setAlphaAllowed(true)
                    .setThemeSyncAllowed(true);

    private final NumberSetting<Float> lineWidth =
            new NumberSetting<>("Line Width", 1.0f, 0.0f, 5.0f)
                    .incremental(0.25f);

    private final List<BlockPos> buddingCache = new ArrayList<>();
    private final List<BlockPos> targets = new ArrayList<>();
    private final Map<BlockPos, Integer> cooldowns = new HashMap<>();
    private int tickCounter = 0;

    private AutoAmethystModule() {
        super(
                "AutoAmethyst",
                "Automatically breaks amethyst buds/clusters without breaking Budding Amethyst.",
                ModuleCategory.MISC
        );

        this.registerSettings(
                range,
                requirePickaxe,
                retryCooldownTicks,
                requireLineOfSight,
                swing,
                render,
                buddingColor,
                targetColor,
                lineWidth
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
    }

    @Subscribe(stage = Stage.PRE)
    public void preTick(EventUpdate event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;

        tickCounter++;

        if (requirePickaxe.getValue() && !isHoldingPickaxe()) return;

        if (!cooldowns.isEmpty()) {
            cooldowns.entrySet().removeIf(e -> (tickCounter - e.getValue()) >= 200);
        }

        buddingCache.clear();
        targets.clear();

        final BlockPos playerPos = mc.player.blockPosition();
        final int r = range.getValue();

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

                        if (!isWithinVanillaReach(shardPos)) continue;

                        if (requireLineOfSight.getValue() && !hasLineOfSight(shardPos)) continue;

                        targets.add(shardPos.immutable());
                    }
                }
            }
        }

        if (targets.isEmpty()) return;

        targets.sort(Comparator.comparingDouble(this::eyeDistanceSqrToCenter));

        for (BlockPos pos : targets) {
            int cd = retryCooldownTicks.getValue();
            if (cd > 0) {
                Integer last = cooldowns.get(pos);
                if (last != null && (tickCounter - last) < cd) continue;
            }

            BlockState state = mc.level.getBlockState(pos);

            if (!isShardDropper(state)) {
                cooldowns.put(pos, tickCounter);
                continue;
            }

            Direction dir = getDirectionForBreak(pos);

            boolean sent = breakBlockPacket(pos, dir, swing.getValue());
            cooldowns.put(pos, tickCounter);

            if (sent) break;
        }
    }

    private boolean isShardDropper(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.BUDDING_AMETHYST) return false;
        if (b == Blocks.AMETHYST_BLOCK) return false;
        return b instanceof AmethystClusterBlock;
    }

    private boolean isHoldingPickaxe() {
        if (mc.player == null) return false;

        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        return item == Items.WOODEN_PICKAXE
                || item == Items.STONE_PICKAXE
                || item == Items.IRON_PICKAXE
                || item == Items.GOLDEN_PICKAXE
                || item == Items.DIAMOND_PICKAXE
                || item == Items.NETHERITE_PICKAXE;
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

    private boolean hasLineOfSight(BlockPos pos) {
        if (mc.player == null || mc.level == null) return false;

        Vec3 eye = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Vec3 target = Vec3.atCenterOf(pos);

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (hit.getType() == HitResult.Type.MISS) return false;
        return hit.getBlockPos().equals(pos);
    }

    private boolean breakBlockPacket(BlockPos pos, Direction dir, boolean doSwing) {
        if (mc.player == null || mc.player.connection == null || mc.level == null) return false;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;

        mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                dir
        ));

        mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                dir
        ));

        if (doSwing) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        return true;
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
        if (!render.getValue()) return;
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
}
