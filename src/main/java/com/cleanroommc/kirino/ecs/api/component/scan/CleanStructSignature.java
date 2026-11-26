package com.cleanroommc.kirino.ecs.api.component.scan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a nested value type for discovery by the struct scanner.
 *
 * <p>The class must be concrete with a no-argument constructor and mutable instance
 * fields for supported value types. Registered nested struct definitions must be acyclic.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CleanStructSignature {
}
