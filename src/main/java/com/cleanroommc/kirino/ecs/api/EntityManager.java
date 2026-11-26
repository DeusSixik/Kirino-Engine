package com.cleanroommc.kirino.ecs.api;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.cleanroommc.kirino.ecs.api.entity.callback.EntityCreateCallback;
import com.cleanroommc.kirino.ecs.api.entity.callback.EntityDestroyCallback;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Accepts and commits entity commands for one ECS runtime session.
 *
 * <p>The manager serializes command admission and structural commit. Commands affect
 * projected composition when accepted. Their storage effects become available after
 * {@link #flush()}. Do not use an observation as a guarantee about a later call.</p>
 */
public interface EntityManager {
    /**
     * Creates an empty, mutable composition filter.
     *
     * @return A new query that initially matches any component composition.
     */
    @NonNull EntityQuery newQuery();

    /**
     * Queues entity creation. The returned handle is reserved until commit.
     *
     * <p>The manager copies the array and borrows its component objects and nested values.
     * The caller may reuse the array after the call and must keep the borrowed values
     * stable until the command is committed or discarded.</p>
     *
     * @param components Registered component payloads, with at most one instance per exact class.
     * @return A reserved handle. Its projected composition is available immediately.
     * @throws NullPointerException     If the array or an element is null.
     * @throws IllegalArgumentException If a component is unregistered, duplicated, or violates its schema.
     * @throws IllegalStateException    If the manager is unavailable or the caller reenters a callback.
     */
    @NonNull CleanEntityHandle createEntity(@NonNull CleanComponent @NonNull ... components);

    /**
     * Queues entity creation. The returned handle is reserved until commit.
     *
     * <p>The manager copies the array and borrows its component objects and nested values.
     * The caller may reuse the array after the call and must keep the borrowed values
     * stable until the command is committed or discarded.</p>
     *
     * @param destroyCallback Action before a committed entity is removed, or null for none.
     * @param createCallback  Action allowed to prepare the payload during commit, or null for none.
     * @param components      Registered component payloads, with at most one instance per exact class.
     * @return A reserved handle. Its projected composition is available immediately.
     * @throws NullPointerException     If the array or an element is null.
     * @throws IllegalArgumentException If a component is unregistered, duplicated, or violates its schema.
     * @throws IllegalStateException    If the manager is unavailable or the caller reenters a callback.
     */
    @NonNull CleanEntityHandle createEntity(@Nullable EntityDestroyCallback destroyCallback,
                                            @Nullable EntityCreateCallback createCallback,
                                            @NonNull CleanComponent @NonNull ... components);

    /**
     * Commits the accepted commands in admission order on the calling thread.
     *
     * <p>Lifecycle callbacks run synchronously during commit. A job scope or reentrant
     * callback can reject this call before any command is applied. Retry an active-job
     * rejection after its execution scopes have released storage. An error during commit
     * may leave earlier commands and callback effects applied. The manager discards the
     * batch and rejects further operations until its runtime is reset or closed.</p>
     *
     * @throws IllegalStateException If an execution is active, the manager is unavailable, or the caller reenters a callback.
     * @throws RuntimeException      If a command or callback fails during commit.
     */
    void flush();
}
