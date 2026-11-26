package com.cleanroommc.kirino.ecs;

import com.google.common.base.Preconditions;

/**
 * Calculates geometric growth and optional shrinkage for primitive pool arrays.
 */
final class PoolCapacity {
    /**
     * Maximum supported row count with headroom below the signed int limit.
     */
    static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private PoolCapacity() {
    }

    /**
     * Validates an explicitly requested pool capacity.
     *
     * @param capacity Requested row count between zero and {@link #MAX_CAPACITY} inclusive.
     * @throws IllegalArgumentException If the request lies outside the supported range.
     */
    static void validateRequested(int capacity) {
        if (!(capacity >= 0 && capacity <= MAX_CAPACITY)) {
            throw new IllegalArgumentException("Invalid pool capacity: " + capacity + ".");
        }
    }

    /**
     * Selects a capacity large enough for a new row.
     *
     * <p>For current capacity {@code c} and required capacity {@code r}, the result is
     * {@code min(MAX_CAPACITY, max(r, c + max(1, floor(c / 2))))}. The sum uses a long
     * to avoid overflow before the limit is applied.</p>
     *
     * @param capacity Current nonnegative capacity in rows.
     * @param required Required capacity in rows.
     * @return The new capacity in rows.
     * @throws OutOfMemoryError If the required count is outside the supported array range.
     */
    static int grow(int capacity, int required) {
        if (required < 0 || required > MAX_CAPACITY) {
            throw new OutOfMemoryError("Required pool capacity exceeds the supported array length.");
        }

        long geometric = (long) capacity + Math.max(1, capacity / 2);
        return (int) Math.min(MAX_CAPACITY, Math.max(geometric, required));
    }

    /**
     * Reduces capacity after occupancy falls to at most one quarter of it.
     *
     * <p>The new size is {@code max(minimum, 2 * rowLimit)} when capacity exceeds the
     * minimum and the threshold is met. The threshold bounds the doubled count.
     * Using the highest live row as the bound preserves interior holes and live indices.</p>
     *
     * @param capacity Current capacity in rows.
     * @param rowLimit Upper exclusive bound containing all live rows.
     * @param minimum  Lower bound for retained capacity.
     * @return The retained capacity in rows.
     */
    static int shrink(int capacity, int rowLimit, int minimum) {
        if (capacity <= minimum || rowLimit > capacity / 4) {
            return capacity;
        }

        return Math.max(minimum, rowLimit * 2);
    }
}
