package com.cleanroommc.kirino.ecs.api.entity.callback;

import com.cleanroommc.kirino.ecs.api.EntityManager;
import org.jspecify.annotations.NonNull;

/**
 * Prepares component payloads on the thread calling {@link EntityManager#flush()}.
 *
 * <p>The callback runs before the values are copied into storage. Access payloads through
 * the supplied context. Do not enqueue commands, flush, change the runtime lifecycle,
 * or wait on work that needs the same manager to finish this commit. External side
 * effects are the callback's responsibility and survive a failed commit.</p>
 */
public interface EntityCreateCallback {
    /**
     * Prepares values for the entity being created.
     *
     * @param createContext Payload access valid on the callback thread until this invocation returns.
     */
    void beforeCreate(@NonNull EntityCreateContext createContext);
}
