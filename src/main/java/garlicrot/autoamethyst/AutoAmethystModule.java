package garlicrot.autoamethyst;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;

import org.rusherhack.core.utils.ColorUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mines one amethyst growth block at a time. START and STOP packets are
 * separated by the *actual* vanilla mining progress for the currently held
 * tool, not by a fixed, one-tick delay.
 *
 * We deliberately do not mutate Minecraft's own MultiPlayerGameMode mining
 * state: its ordinary mouse-input loop may cancel simulated mining when the
 * attack key is up. This session owns its packet lifecycle and safety checks.
 */
public final class AutoAmethystModule extends ToggleableModule {
    // Scan frequently enough to detect freshly grown crystals, but avoid a full
    // cube scan on every frame. Reach is read from the vanilla player attribute.
    private static final int SCAN_RADIUS = 6;
    private static final int SCAN_EVERY_TICKS = 2;
    private static final int VERIFY_TIMEOUT_TICKS = 16;
    private static final int FINISH_SAFETY_TICKS = 2;
    private static final int RETRY_BASE_TICKS = 10;
    private static final int MINING_TIMEOUT_TICKS = 120;
    private static final int BUDDING_COLOR = 0xFFAA3CFF;
    private static final int TARGET_COLOR = 0xFF3CFF78;
    private static final int MINING_COLOR = 0xFFFFCD3C;
    private static final int CLEAN_RETRIES_EVERY_TICKS = 200;
    private static final int RETRY_TTL_TICKS = 1200;

    private static AutoAmethystModule instance;
    private final Minecraft mc = Minecraft.getInstance();

    private final EnumSetting<HarvestRules.HarvestMode> harvestMode =
            new EnumSetting<>("Harvest", "Mature Only collects shards; All Growth also removes immature buds.",
                    HarvestRules.HarvestMode.MATURE_ONLY);
    private final BooleanSetting render =
            new BooleanSetting("Show Targets", "Highlight Budding Amethyst and crystals within mining reach.", true);

    // World state is never retained across dimensions / disconnects.
    private ClientLevel trackedWorld;
    private long tick;
    private long nextScanTick;
    private AmethystTargetFinder.Scan scan = new AmethystTargetFinder.Scan(List.of(), List.of());
    private final Map<BlockPos, Retry> retries = new HashMap<>();

    // Only one active mining operation, or one pending server confirmation.
    private BlockPos miningPos;
    private Direction miningFace;
    private Block miningBlock;
    private int miningHotbarSlot;
    private long miningStartTick;
    private float miningProgress;
    private int finishingTicks;
    private boolean verifying;
    private long verifyStartTick;

    private record Retry(int failures, long retryAfter, long updatedAt) {}

    private AutoAmethystModule() {
        super("AutoAmethyst", "Safely harvest amethyst buds/clusters using normal mining progression.",
                ModuleCategory.MISC);
        registerSettings(harvestMode, render);
    }

    public static synchronized AutoAmethystModule getInstance() {
        if (instance == null) instance = new AutoAmethystModule();
        return instance;
    }

    @Override
    public void onEnable() {
        reset(true);
    }

    @Override
    public void onDisable() {
        reset(true);
    }

    private void reset(boolean abort) {
        abandonMining(abort);
        scan = new AmethystTargetFinder.Scan(List.of(), List.of());
        retries.clear();
        trackedWorld = null;
        tick = 0;
        nextScanTick = 0;
    }

    private void abandonMining(boolean abort) {
        // ABORT only an unfinished operation; a completed STOP must not be
        // followed by an unrelated ABORT for the same target.
        if (abort && miningPos != null && !verifying) {
            sendDig(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, miningPos, miningFace);
        }
        miningPos = null;
        miningFace = null;
        miningBlock = null;
        verifying = false;
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(EventUpdate event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) {
            // The old world might already be gone. Don't send packets into a new connection.
            reset(false);
            return;
        }
        if (trackedWorld != mc.level) {
            reset(false);
            trackedWorld = mc.level;
        }
        tick++;

        if (tick % CLEAN_RETRIES_EVERY_TICKS == 0) {
            retries.entrySet().removeIf(e -> tick - e.getValue().updatedAt() > RETRY_TTL_TICKS);
        }

        if (tick >= nextScanTick) {
            scan = AmethystTargetFinder.scan(mc.level, mc.player,
                    harvestMode.getValue(), SCAN_RADIUS, allowedReach());
            nextScanTick = tick + SCAN_EVERY_TICKS;
        }

        // Successful mining need not waste the old fixed 16-tick verification
        // period: switch targets immediately when the server changes the block.
        if (verifying && !finishVerification()) return;

        if (!canAutoMine()) {
            abandonMining(true);
            return;
        }

        if (miningPos != null) {
            progressMining();
        } else {
            beginMining();
        }
    }

    private boolean canAutoMine() {
        // No extra settings: manual input, GUIs and spectator mode always win.
        return mc.screen == null && !mc.player.isSpectator()
                && !mc.options.keyAttack.isDown() && !mc.player.isUsingItem();
    }

    private double allowedReach() {
        return HarvestRules.vanillaReach(mc.player.blockInteractionRange(),
                mc.player.getAbilities().instabuild);
    }

    private void beginMining() {
        for (AmethystTargetFinder.Target candidate : scan.targets()) {
            BlockPos pos = candidate.pos();
            Retry retry = retries.get(pos);
            if (retry != null && tick < retry.retryAfter()) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (!AmethystTargetFinder.isEligible(mc.level, pos, state, harvestMode.getValue())) continue;
            AmethystTargetFinder.Target current = AmethystTargetFinder.visibleHit(
                    mc.level, mc.player, pos, state, allowedReach());
            if (current == null) continue;

            // The server tracks mining time from START. We don't issue STOP
            // until the sum of vanilla block-damage increments reaches 1.0,
            // plus a small safety margin for server/client tick alignment.
            sendDig(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, current.face());
            miningPos = pos;
            miningFace = current.face();
            miningBlock = state.getBlock();
            miningHotbarSlot = mc.player.getInventory().selected;
            miningStartTick = tick;
            miningProgress = 0.0f;
            finishingTicks = 0;
            mc.player.swing(InteractionHand.MAIN_HAND);

            // Creative mode / instant-mining can change the block right away.
            if (mc.level.getBlockState(pos).getBlock() != miningBlock) beginVerification();
            return;
        }
    }

    private void progressMining() {
        BlockState state = mc.level.getBlockState(miningPos);
        if (state.getBlock() != miningBlock) {
            beginVerification();
            return;
        }
        if (mc.player.getInventory().selected != miningHotbarSlot
                || !AmethystTargetFinder.isEligible(mc.level, miningPos, state, harvestMode.getValue())) {
            abandonMining(true);
            return;
        }
        if (AmethystTargetFinder.visibleHit(mc.level, mc.player, miningPos, state,
                allowedReach()) == null) {
            abandonMining(true);
            return;
        }
        if (tick - miningStartTick >= MINING_TIMEOUT_TICKS) {
            BlockPos failed = miningPos;
            abandonMining(true);
            markFailure(failed);
            return;
        }

        float damage = state.getDestroyProgress(mc.player, mc.level, miningPos);
        if (Float.isFinite(damage) && damage > 0.0f) {
            miningProgress += damage;
        }
        if ((tick - miningStartTick) % 5 == 0) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (miningProgress >= 1.0f && ++finishingTicks >= FINISH_SAFETY_TICKS) {
            sendDig(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, miningPos, miningFace);
            beginVerification();
        }
    }

    private void beginVerification() {
        // Packet submission isn't server confirmation. Wait for the target's
        // actual client-world state to be updated by the server.
        verifying = true;
        verifyStartTick = tick;
    }

    /** @return true if the verification ended; another target may start now. */
    private boolean finishVerification() {
        BlockPos finished = miningPos;
        if (finished == null) {
            abandonMining(false);
            return true;
        }

        HarvestRules.Verification result = HarvestRules.verification(
                mc.level.getBlockState(finished).getBlock() != miningBlock,
                tick - verifyStartTick, VERIFY_TIMEOUT_TICKS);
        if (result == HarvestRules.Verification.WAITING) return false;

        if (result == HarvestRules.Verification.CONFIRMED) {
            retries.remove(finished);
        } else {
            markFailure(finished);
        }
        abandonMining(false);
        return true;
    }

    private void sendDig(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction face) {
        if (mc.player == null || mc.player.connection == null || face == null) return;
        mc.player.connection.send(new ServerboundPlayerActionPacket(action, pos, face));
    }

    private void markFailure(BlockPos pos) {
        Retry old = retries.get(pos);
        int failures = old == null ? 1 : Math.min(6, old.failures() + 1);
        int delay = HarvestRules.retryDelayTicks(RETRY_BASE_TICKS, failures);
        retries.put(pos.immutable(), new Retry(failures, tick + delay, tick));
    }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (!render.getValue() || mc.player == null || mc.level == null || trackedWorld != mc.level) return;
        IRenderer3D renderer = event.getRenderer();
        renderer.begin(event.getMatrixStack());
        renderer.setLineWidth(1.0f);

        for (BlockPos budding : scan.budding()) {
            renderer.drawBox(budding, true, true,
                    ColorUtils.transparency(BUDDING_COLOR, 60));
        }
        for (AmethystTargetFinder.Target target : scan.targets()) {
            renderer.drawBox(target.pos(), true, true,
                    ColorUtils.transparency(TARGET_COLOR, 80));
        }
        if (miningPos != null && !verifying) {
            renderer.drawBox(miningPos, true, true,
                    ColorUtils.transparency(MINING_COLOR, 90));
        }
        renderer.end();
    }
}
