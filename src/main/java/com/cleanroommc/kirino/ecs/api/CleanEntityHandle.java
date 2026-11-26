package com.cleanroommc.kirino.ecs.api;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * References a generation of an entity slot in one manager.
 *
 * <p>The manager serializes handle operations and command admission. An accepted destroy
 * immediately invalidates the handle. Accepted SET and ADD commands borrow the supplied
 * object and its nested values until commit or discard. Keep those payloads stable during
 * that period. Observations such as {@link #valid()} describe a single call.</p>
 */
public interface CleanEntityHandle {
    /**
     * Reports whether this handle can still accept entity commands.
     *
     * @return Whether the handle matches a reserved or live slot in a healthy running manager.
     */
    boolean valid();

    /**
     * Reports whether the handle refers to a committed live entity.
     *
     * @return Whether the handle matches a live slot in a healthy running manager, with no accepted destroy.
     */
    boolean isAlive();

    /**
     * Checks the projected component composition, including accepted structural changes.
     *
     * @param component Exact component class to check.
     * @return Whether the handle is valid and its projected composition includes the class.
     * @throws NullPointerException  If the class is null.
     * @throws IllegalStateException If the manager is unavailable or the caller reenters a callback.
     */
    boolean contains(@NonNull Class<? extends CleanComponent> component);

    /**
     * Reconstructs a copy from committed storage. Writes to the copy stay local to the caller.
     *
     * @param component Exact component class to read.
     * @param <T>       Component type.
     * @return An independent copy, or empty if the handle is no longer alive or the committed composition lacks the class.
     * @throws NullPointerException  If the class is null.
     * @throws IllegalStateException If jobs are active, the manager is unavailable, or the caller reenters a callback.
     */
    @NonNull <T extends CleanComponent> Optional<T> readComponent(@NonNull Class<T> component);

    /**
     * Queues destruction and invalidates this handle upon acceptance.
     *
     * @return Whether the command was accepted. A stale or pending-destroy handle returns false.
     * @throws IllegalStateException If the manager is unavailable or the caller reenters a callback.
     */
    boolean tryDestroy();

    /**
     * Queues replacement of a component.
     *
     * @param component Registered replacement value for a type in the projected composition.
     * @return Whether the command was accepted. A stale handle or an absent type returns false.
     * @throws NullPointerException     If an eligible target receives a null argument.
     * @throws IllegalArgumentException If an eligible replacement violates its schema.
     * @throws IllegalStateException    If the manager is unavailable or the caller reenters a callback.
     */
    boolean trySetComponent(@NonNull CleanComponent component);

    /**
     * Queues addition of a component type.
     *
     * @param component Registered component value to add.
     * @return Whether the command was accepted. A stale handle or duplicate type returns false.
     * @throws NullPointerException     If an eligible target receives a null argument.
     * @throws IllegalArgumentException If an eligible value lacks a schema or violates it.
     * @throws IllegalStateException    If the manager is unavailable or the caller reenters a callback.
     */
    boolean tryAddComponent(@NonNull CleanComponent component);

    /**
     * Queues removal of a component type.
     *
     * @param component Exact component class to remove.
     * @return Whether the command was accepted. A stale handle or absent type returns false.
     * @throws NullPointerException  If an eligible target receives a null argument.
     * @throws IllegalStateException If the manager is unavailable or the caller reenters a callback.
     */
    boolean tryRemoveComponent(@NonNull Class<? extends CleanComponent> component);
}
