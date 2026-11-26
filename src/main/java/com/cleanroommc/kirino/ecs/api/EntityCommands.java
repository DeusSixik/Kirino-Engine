package com.cleanroommc.kirino.ecs.api;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.cleanroommc.kirino.ecs.api.entity.callback.EntityCreateCallback;
import com.cleanroommc.kirino.ecs.api.entity.callback.EntityDestroyCallback;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Accepts deferred structural commands from partitions of one job launch.
 *
 * <p>The manager serializes commands from the partitions. Use the writer only inside
 * this launch's job calls. ID-based operations target live entities in matching committed
 * pools. Pass an entity ID from this launch instead of saving it across launches, since
 * IDs can be reused. The returned booleans report admission, and changes take effect
 * at {@link EntityManager#flush()}.</p>
 *
 * <p>Accepted CREATE, SET, and ADD commands borrow component objects and nested values
 * until commit or discard. Keep borrowed values stable. CREATE copies the array of references.</p>
 */
public interface EntityCommands {
    /**
     * Queues entity creation without lifecycle callbacks.
     *
     * @param components Registered component payloads, with at most one instance per exact class.
     * @return A reserved handle whose projected composition is immediately available.
     * @throws NullPointerException     If the array or an element is null.
     * @throws IllegalArgumentException If a component is unregistered, duplicated, or violates its schema.
     * @throws IllegalStateException    If the launch has ended or the manager is unavailable.
     */
    @NonNull
    CleanEntityHandle createEntity(@NonNull CleanComponent @NonNull ... components);

    /**
     * Queues entity creation with callbacks that run during later commit or cleanup.
     *
     * @param destroyCallback Action before committed data is removed, or null for none.
     * @param createCallback  Action allowed to prepare the payload during commit, or null for none.
     * @param components      Registered component payloads, with at most one instance per exact class.
     * @return A reserved handle whose projected composition is immediately available.
     * @throws NullPointerException     If the array or an element is null.
     * @throws IllegalArgumentException If a component is unregistered, duplicated, or violates its schema.
     * @throws IllegalStateException    If the launch has ended or the manager is unavailable.
     */
    @NonNull
    CleanEntityHandle createEntity(@Nullable EntityDestroyCallback destroyCallback,
                                   @Nullable EntityCreateCallback createCallback,
                                   @NonNull CleanComponent @NonNull ... components);

    /**
     * Queues destruction and marks the entity as pending removal on acceptance.
     *
     * @param entityID Manager-local entity ID from this launch.
     * @return Whether the command was accepted. Ineligible IDs return false.
     * @throws IllegalStateException If the launch has ended or the manager is unavailable.
     */
    boolean tryDestroy(int entityID);

    /**
     * Queues replacement of a component value.
     *
     * @param entityID  Manager-local entity ID from this launch.
     * @param component Replacement value for a type in the projected composition.
     * @return Whether the command was accepted. An ineligible ID or an absent type returns false.
     * @throws NullPointerException     If an eligible target receives a null argument.
     * @throws IllegalArgumentException If an accepted component type has an invalid payload.
     * @throws IllegalStateException    If the launch has ended or the manager is unavailable.
     */
    boolean trySetComponent(int entityID, @NonNull CleanComponent component);

    /**
     * Queues addition of a component type.
     *
     * @param entityID  Manager-local entity ID from this launch.
     * @param component Registered payload value to add.
     * @return Whether the command was accepted. An ineligible ID or a duplicate type returns false.
     * @throws NullPointerException     If an eligible target receives a null argument.
     * @throws IllegalArgumentException If an accepted component type has an invalid payload.
     * @throws IllegalStateException    If the launch has ended or the manager is unavailable.
     */
    boolean tryAddComponent(int entityID, @NonNull CleanComponent component);

    /**
     * Queues removal of a component type.
     *
     * @param entityID  Manager-local entity ID from this launch.
     * @param component Exact component class to remove.
     * @return Whether the command was accepted. An ineligible ID or an absent type returns false.
     * @throws NullPointerException  If an eligible target receives a null argument.
     * @throws IllegalStateException If the launch has ended or the manager is unavailable.
     */
    boolean tryRemoveComponent(int entityID, @NonNull Class<? extends CleanComponent> component);
}
