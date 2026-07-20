package aaronpost.atpcore.gui;

import org.bukkit.event.inventory.ClickType;

/**
 * Reusable ±1/±5 click adjuster. LEFT/SHIFT_LEFT = +1/+5, RIGHT/SHIFT_RIGHT = -1/-5.
 * Result is clamped to {@code [min, max]}.
 */
public final class ClickQuantityAdjuster {
    private ClickQuantityAdjuster() {}

    public enum Outcome {
        UNCHANGED,
        INCREMENTED,
        DECREMENTED,
        AT_MAX,
        AT_MIN
    }

    public static final class Result {
        public final int newAmount;
        public final Outcome outcome;
        public Result(int newAmount, Outcome outcome) {
            this.newAmount = newAmount;
            this.outcome = outcome;
        }
        public boolean changed() {
            return outcome == Outcome.INCREMENTED || outcome == Outcome.DECREMENTED;
        }
    }

    public static Result adjust(ClickType click, int current, int min, int max) {
        int delta = switch (click) {
            case LEFT -> +1;
            case SHIFT_LEFT -> +5;
            case RIGHT -> -1;
            case SHIFT_RIGHT -> -5;
            default -> 0;
        };
        if (delta == 0) return new Result(current, Outcome.UNCHANGED);
        if (delta > 0 && current >= max) return new Result(current, Outcome.AT_MAX);
        if (delta < 0 && current <= min) return new Result(current, Outcome.AT_MIN);
        int next = Math.max(min, Math.min(max, current + delta));
        return new Result(next, delta > 0 ? Outcome.INCREMENTED : Outcome.DECREMENTED);
    }
}
