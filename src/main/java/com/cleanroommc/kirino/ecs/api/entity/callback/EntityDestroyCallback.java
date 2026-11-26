package com.cleanroommc.kirino.ecs.api.entity.callback;

import com.cleanroommc.kirino.ecs.api.EntityManager;
import org.jspecify.annotations.NonNull;

/**
 * Reads committed data before removal during {@link EntityManager#flush()} or runtime cleanup.
 *
 * <p>The callback runs on the caller's thread. Use the supplied context and follow
 * the same manager reentry and external side-effect rules as {@link EntityCreateCallback}.
 * A callback attempted during a failed commit is not replayed during cleanup.</p>
 */
public interface EntityDestroyCallback {
    /**
     * Handles an entity while its committed data remains available.
     *
     * @param destroyContext Committed-data access valid on the callback thread until this invocation returns.
     */
    void beforeDestroy(@NonNull EntityDestroyContext destroyContext);
}
