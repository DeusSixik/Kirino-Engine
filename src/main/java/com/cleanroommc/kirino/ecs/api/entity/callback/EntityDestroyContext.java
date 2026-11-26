package com.cleanroommc.kirino.ecs.api.entity.callback;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import org.jspecify.annotations.NonNull;

/**
 * Provides access to committed entity data during a destroy callback.
 *
 * <p>Use this context on the callback thread and discard it on return. Primitive getters
 * require a nonempty path of registered field names ending at a column of the exact
 * requested type. They read from the entity's current storage row.</p>
 */
public interface EntityDestroyContext {
    /**
     * Reconstructs an independent component copy from committed data.
     *
     * @param component Exact component class present on the entity.
     * @param <T>       Component type.
     * @return A copy of the committed value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the entity lacks the component.
     * @throws NullPointerException     If the component class is null.
     */
    @NonNull
    <T extends CleanComponent> T getComponent(@NonNull Class<T> component);

    /**
     * Reads a byte field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code byte}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    byte getByte(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);

    /**
     * Reads a short field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code short}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    short getShort(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);

    /**
     * Reads a int field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code int}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    int getInt(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);

    /**
     * Reads a long field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code long}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    long getLong(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);

    /**
     * Reads a float field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code float}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    float getFloat(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);

    /**
     * Reads a double field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code double}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    double getDouble(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);

    /**
     * Reads a boolean field from committed storage.
     *
     * @param component Exact component class present on the entity.
     * @param path      Nonempty path to a field of type {@code boolean}.
     * @return The current field value.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the component, path, or primitive type is invalid.
     * @throws NullPointerException     If the component class, path, or any path segment is null.
     */
    boolean getBool(@NonNull Class<? extends CleanComponent> component, @NonNull String @NonNull ... path);
}
