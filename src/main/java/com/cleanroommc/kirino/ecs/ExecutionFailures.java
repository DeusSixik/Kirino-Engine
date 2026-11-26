package com.cleanroommc.kirino.ecs;

import com.google.common.base.Throwables;

import java.util.concurrent.CompletionException;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/**
 * Combines failures from preparation, workers, callbacks, and cleanup without cause cycles.
 */
final class ExecutionFailures {
    private ExecutionFailures() {
    }

    /**
     * Preserves the first failure and attaches a distinct subsequent failure when possible.
     *
     * @param primary   Existing failure, or null.
     * @param secondary Later failure, possibly wrapped in a completion exception, or null.
     * @return The combined failure, or null if neither input failed.
     */
    static Throwable merge(Throwable primary, Throwable secondary) {
        secondary = unwrap(secondary);
        if (primary == null) {
            return secondary;
        }
        if (secondary == null || primary == secondary) {
            return primary;
        }
        if (contains(primary, secondary)) {
            return primary;
        }
        if (contains(secondary, primary)) {
            return primary;
        }
        primary.addSuppressed(secondary);
        return primary;
    }

    /**
     * Keeps an enclosing failure when it already includes the recorded cause.
     *
     * @param primary   Existing failure, or null.
     * @param secondary Later failure, or null.
     * @return The combined failure, preferring the enclosing cause when it contains the primary.
     */
    static Throwable mergeEnclosing(Throwable primary, Throwable secondary) {
        secondary = unwrap(secondary);
        if (primary != null && secondary != null && contains(secondary, primary)) {
            return secondary;
        }
        return merge(primary, secondary);
    }

    private static boolean contains(Throwable root, Throwable target) {
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        IdentityHashMap<Throwable, Boolean> visited = new IdentityHashMap<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Throwable next = pending.removeLast();
            if (next == target) {
                return true;
            }
            if (visited.put(next, Boolean.TRUE) != null) {
                continue;
            }
            if (next.getCause() != null) {
                pending.add(next.getCause());
            }
            for (Throwable suppressed : next.getSuppressed()) {
                pending.add(suppressed);
            }
        }
        return false;
    }

    /**
     * Unwraps completion exceptions with an underlying cause.
     *
     * @param failure Failure to unwrap, or null.
     * @return The underlying cause, the original failure, or null.
     */
    static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    /**
     * Propagates an unchecked failure unchanged and wraps checked exceptions.
     *
     * @param failure Non-null failure to report.
     * @throws CompletionException If the underlying failure is checked.
     */
    static void rethrow(Throwable failure) {
        Throwables.throwIfUnchecked(failure);
        throw new CompletionException(failure);
    }
}
