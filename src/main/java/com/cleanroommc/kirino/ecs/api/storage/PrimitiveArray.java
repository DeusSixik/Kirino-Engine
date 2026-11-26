package com.cleanroommc.kirino.ecs.api.storage;

import org.jspecify.annotations.NonNull;

/**
 * A typed, mutable view of one primitive column in one archetype pool.
 *
 * <p>A job may use an injected column only during its launch. Do not retain it, hand it to
 * untracked work, or use a column for rows outside the job's assigned work. The index
 * passed to the job is a storage row. Column length describes capacity and can include
 * unused rows. Setters write directly to committed storage and their effects can survive
 * a failed job.</p>
 *
 * <p>Jobs may share the same column. The scheduler protects against structural changes
 * while work is active. The caller is responsible for ordering or synchronizing accesses
 * that overlap on the same rows and include a write.</p>
 */
public interface PrimitiveArray {

    /**
     * Reads a byte value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    byte getByte(int index);

    /**
     * Reads a short value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    short getShort(int index);

    /**
     * Reads a int value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    int getInt(int index);

    /**
     * Reads a long value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    long getLong(int index);

    /**
     * Reads a float value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    float getFloat(int index);

    /**
     * Reads a double value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    double getDouble(int index);

    /**
     * Reads a boolean value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @return The value stored at the row.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    boolean getBool(int index);

    /**
     * Writes a byte value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setByte(int index, byte value);

    /**
     * Writes a short value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setShort(int index, short value);

    /**
     * Writes a int value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setInt(int index, int value);

    /**
     * Writes a long value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setLong(int index, long value);

    /**
     * Writes a float value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setFloat(int index, float value);

    /**
     * Writes a double value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setDouble(int index, double value);

    /**
     * Writes a boolean value.
     *
     * @param index Occupied row assigned to the job, between zero and {@link #length()} exclusive.
     * @param value The value to store.
     * @throws IllegalStateException     If the column has a different primitive type.
     * @throws IndexOutOfBoundsException If the row lies outside the backing column.
     */
    void setBool(int index, boolean value);

    /**
     * Returns the number of slots in the backing column.
     *
     * @return Column capacity in rows.
     */
    int length();

    /**
     * Returns the one primitive type accepted by this column.
     *
     * @return The storage type.
     */
    @NonNull
    PrimitiveArrayType type();
}
