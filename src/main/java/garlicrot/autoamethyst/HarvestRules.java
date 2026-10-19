package garlicrot.autoamethyst;

/** Minecraft-independent rules for crystal harvesting and vanilla reach. */
public final class HarvestRules {
    private HarvestRules() {}

    public enum Stage { SMALL, MEDIUM, LARGE, CLUSTER }

    /** Keep the everyday choice obvious; all-growth is deliberately opt-in. */
    public enum HarvestMode {
        MATURE_ONLY("Mature Clusters"),
        ALL_GROWTH("All Growth");

        private final String label;
        HarvestMode(String label) { this.label = label; }
        @Override public String toString() { return label; }

        public boolean accepts(Stage stage) {
            return stage != null && (this == ALL_GROWTH || stage == Stage.CLUSTER);
        }
    }

    /** Never exceed vanilla reach, even if another mod changes the range attribute. */
    public static double vanillaReach(double playerRange, boolean creative) {
        if (!Double.isFinite(playerRange)) return 0.0;
        return Math.max(0.0, Math.min(playerRange, creative ? 5.0 : 4.5));
    }

    public enum Verification { CONFIRMED, WAITING, TIMED_OUT }

    /** A world-state change confirms immediately; timeout only affects missing updates. */
    public static Verification verification(boolean blockChanged, long elapsedTicks, int timeoutTicks) {
        if (blockChanged) return Verification.CONFIRMED;
        return elapsedTicks >= timeoutTicks ? Verification.TIMED_OUT : Verification.WAITING;
    }

    /** Back off when a server rejects mining rather than continually sending packets. */
    public static int retryDelayTicks(int baseTicks, int consecutiveFailures) {
        int base = Math.max(2, baseTicks);
        int shift = Math.max(0, Math.min(4, consecutiveFailures - 1));
        return Math.min(160, base * (1 << shift));
    }
}
