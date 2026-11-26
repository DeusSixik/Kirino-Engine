package com.cleanroommc.kirino.ecs.api.job;

import org.jspecify.annotations.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a mutable job field to a value supplied to {@link JobScheduler}.
 *
 * <p>The map must contain the key even when its value is null. A primitive field requires
 * its boxed type. A reference field accepts null or an instance of its declared type.
 * The injected objects may be shared across partitions and launches. Static fields are
 * ignored, final fields are rejected, and an instance field can declare only one binding
 * annotation.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JobExternalDataQuery {
    /**
     * Selects the external binding key.
     *
     * @return An explicit key, or the empty string to use the Java field name.
     */
    @NonNull String value() default "";
}
