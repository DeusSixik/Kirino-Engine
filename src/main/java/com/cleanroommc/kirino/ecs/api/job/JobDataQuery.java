package com.cleanroommc.kirino.ecs.api.job;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a mutable {@link com.cleanroommc.kirino.ecs.api.storage.PrimitiveArray} field to a flattened component column.
 *
 * <p>The field must have exactly the {@code PrimitiveArray} type. Registration validates the
 * component and path. Static fields are ignored and final fields are rejected. The query
 * must select archetypes that supply this column.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JobDataQuery {
    /**
     * Selects the component that owns the column.
     *
     * @return The exact registered component class.
     */
    @NonNull Class<?> componentClass();

    /**
     * Selects a column within the component.
     *
     * @return Field names from the component to a primitive leaf, such as {@code {"position", "x"}}.
     */
    @NonNull String @NonNull [] fieldAccessChain();
}
