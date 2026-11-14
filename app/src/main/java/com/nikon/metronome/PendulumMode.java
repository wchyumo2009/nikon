package com.nikon.metronome;

/** The three pendulum behaviours from the requirement. */
public enum PendulumMode {
    /** Pivot at the top of the case, rod and bob swing below (wall clock style). */
    TOP,
    /** Pivot at the bottom of the case, rod swings above (classic mechanical metronome). */
    BOTTOM,
    /** Suspended spring that stretches and sways on every beat. */
    SPRING;

    public static PendulumMode fromOrdinal(int ordinal) {
        PendulumMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return BOTTOM;
        }
        return values[ordinal];
    }
}
