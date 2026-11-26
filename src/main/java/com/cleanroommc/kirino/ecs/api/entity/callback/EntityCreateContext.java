package com.cleanroommc.kirino.ecs.api.entity.callback;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import org.jspecify.annotations.NonNull;

/**
 * Provides access to the pending component payload during a create callback.
 *
 * <p>The context is borrowed on the callback thread until the invocation returns.
 * The callback may edit payload values to prepare them for storage.</p>
 */
public interface EntityCreateContext {
    /**
     * Finds the supplied payload object for an exact component class.
     *
     * @param component Component class present in the creation payload.
     * @param <T>       Component type.
     * @return The pending payload object, including changes made by the callback.
     * @throws IllegalStateException    If the context is inactive.
     * @throws IllegalArgumentException If the class is absent.
     * @throws NullPointerException     If the class is null.
     */
    @NonNull
    <T extends CleanComponent> T getComponent(@NonNull Class<T> component);
}
