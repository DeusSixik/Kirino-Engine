package com.cleanroommc.kirino.ecs.api.storage;

/**
 * Identifies the primitive operation supported by a {@link PrimitiveArray}.
 */
public enum PrimitiveArrayType {
    /**
     * Stores values of type {@code byte}.
     */
    BYTE,
    /**
     * Stores values of type {@code short}.
     */
    SHORT,
    /**
     * Stores values of type {@code int}.
     */
    INT,
    /**
     * Stores values of type {@code long}.
     */
    LONG,
    /**
     * Stores values of type {@code float}.
     */
    FLOAT,
    /**
     * Stores values of type {@code double}.
     */
    DOUBLE,
    /**
     * Stores values of type {@code boolean}.
     */
    BOOL
}
