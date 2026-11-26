package com.cleanroommc.kirino.schemata.collections;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.AbstractObjectList;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.RandomAccess;
import java.util.function.IntFunction;

/**
 * Fixed-size list view of an owned array of references.
 *
 * <p>List mutators reject writes. The array exposed by {@link #elements()} can replace
 * entries, and those changes are visible through the list. Factories copy references.
 * They share the objects stored at those references.</p>
 *
 * @param <TValue> Element type.
 */
public final class ObjectArrayView<TValue> extends AbstractObjectList<TValue> implements RandomAccess, Cloneable {
    private static final ObjectArrayView<?> EMPTY = new ObjectArrayView<>(new Object[0]);
    /**
     * Backing array with one slot per list entry.
     */
    private final TValue[] elements;

    private ObjectArrayView(TValue[] elements) {
        this.elements = elements;
    }

    /**
     * Returns the shared empty view backed by an empty {@code Object[]}.
     *
     * @param <TValue> Element type.
     * @return An empty view.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <TValue> ObjectArrayView<TValue> of() {
        return (ObjectArrayView<TValue>) EMPTY;
    }

    /**
     * Copies the supplied array into a fixed-size view.
     *
     * @param values Source array of non-null references.
     * @param <TValue> Element type.
     * @return A view backed by a shallow clone with the same runtime array type.
     * @throws NullPointerException If the array or an element is null.
     */
    @SafeVarargs
    @NonNull
    public static <TValue> ObjectArrayView<TValue> of(@NonNull TValue @NonNull ... values) {
        return copyOf(values);
    }

    /**
     * Copies a collection in iteration order.
     *
     * @param collection Source of non-null references.
     * @param <TValue> Element type.
     * @return A snapshot with an {@code Object[]} backing array.
     * @throws NullPointerException If the collection or an element is null.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static <TValue> ObjectArrayView<TValue> copyOf(@NonNull Collection<? extends @NonNull TValue> collection) {
        Preconditions.checkNotNull(collection);

        return checked((TValue[]) collection.toArray());
    }

    /**
     * Copies a collection into an array whose runtime type is selected by the caller.
     *
     * @param collection Source of non-null references.
     * @param arrayFactory Factory supplying arrays of the desired element type.
     * @param <TValue> Element type.
     * @return A view backed by the typed array returned by the collection.
     * @throws NullPointerException If an argument or element is null.
     */
    @NonNull
    public static <TValue> ObjectArrayView<TValue> copyOf(@NonNull Collection<? extends @NonNull TValue> collection,
                                                        @NonNull IntFunction<TValue @NonNull []> arrayFactory) {
        Preconditions.checkNotNull(collection);
        Preconditions.checkNotNull(arrayFactory);

        return checked(collection.toArray(arrayFactory));
    }

    /**
     * Copies the input array while preserving its runtime element type.
     *
     * @param values Source array of non-null references.
     * @param <TValue> Element type.
     * @return A view backed by a shallow clone.
     * @throws NullPointerException If the array or an element is null.
     */
    @SafeVarargs
    @NonNull
    public static <TValue> ObjectArrayView<TValue> copyOf(@NonNull TValue @NonNull ... values) {
        Preconditions.checkNotNull(values);

        return checked(values.clone());
    }

    private static <TValue> ObjectArrayView<TValue> checked(TValue[] values) {
        for (TValue value : values) {
            Preconditions.checkNotNull(value);
        }
        return new ObjectArrayView<>(values);
    }

    /**
     * Exposes the live backing array for direct traversal.
     *
     * <p>The length is exactly {@link #size()}. Use an {@code Object[]} reference for
     * views from {@link #of()} or {@link #copyOf(Collection)}. Use the typed factory or
     * an array input when a specific runtime array type is required. Coordinate mutations
     * and concurrent readers with the view's owner. Do not insert null elements.</p>
     *
     * @return The actual mutable backing array.
     */
    public TValue @NonNull [] elements() {
        return elements;
    }

    /**
     * Reads the current element at an index.
     *
     * @param index Zero-based index in the view.
     * @return The current array element.
     * @throws IndexOutOfBoundsException If the index is outside the view.
     */
    @Override
    public TValue get(int index) {
        return elements[index];
    }

    /**
     * Returns the fixed size of the view.
     *
     * @return The number of array slots.
     */
    @Override
    public int size() {
        return elements.length;
    }

    /**
     * Copies the backing array and shares the referenced values.
     *
     * @return A view with independent array storage.
     * @throws NullPointerException If a backing array write introduced a null element.
     */
    @Override
    @NonNull
    public ObjectArrayView<TValue> clone() {
        return copyOf(elements);
    }
}
