package com.cleanroommc.kirino.ecs;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors a completion for callers and keeps observer cancellation independent of internal drain accounting.
 */
final class ObservedCompletion extends CompletableFuture<Void> {
    /**
     * Completion used for admission, storage lifetime, and flow dependencies.
     */
    private final CompletableFuture<Void> actual;

    ObservedCompletion(CompletableFuture<Void> actual) {
        this.actual = actual;
        actual.whenComplete((value, failure) -> {
            if (failure == null) {
                complete(null);
            } else {
                completeExceptionally(failure);
            }
        });
    }

    /**
     * Recovers the internal completion from an ECS observer.
     *
     * @param future ECS observer or external future supplied to a system.
     * @return The underlying completion, or the external future itself.
     */
    static CompletableFuture<Void> actual(CompletableFuture<Void> future) {
        return future instanceof ObservedCompletion observed ? observed.actual : future;
    }
}
