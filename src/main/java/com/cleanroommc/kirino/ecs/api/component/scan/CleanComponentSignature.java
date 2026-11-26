package com.cleanroommc.kirino.ecs.api.component.scan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a concrete {@link com.cleanroommc.kirino.ecs.api.component.CleanComponent} for discovery.
 *
 * <p>The class must have a no-argument constructor. The scanner selects fields of
 * registered scalar and struct types. Selected fields must be mutable instance fields.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CleanComponentSignature {
}
