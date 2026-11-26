package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.storage.PrimitiveArray;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.cleanroommc.kirino.schemata.collections.ObjectArrayView;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Stores committed rows for one exact component composition.
 *
 * <p>The manager serializes structural changes against job admission. A borrowed row
 * or column is valid for its execution scope. Read and write access to the same data
 * from overlapping jobs requires ordering by the caller.</p>
 */
abstract class ArchetypeDataPool {
    /**
     * Composition snapshot. Treat the backing array as read-only.
     */
    public final ObjectArrayView<Class<? extends CleanComponent>> components;

    /**
     * Allocated row capacity, including holes and unused slots.
     */
    protected int currentSize;
    /**
     * Initial capacity in rows and lower bound for automatic shrinking.
     */
    public final int initSize;

    /**
     * Registry owning the schemas for this pool.
     */
    protected final ComponentRegistry componentRegistry;

    /**
     * Returns the allocated storage capacity.
     *
     * @return The number of allocated rows, including unoccupied rows.
     */
    public final int getCurrentSize() {
        return currentSize;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    ArchetypeDataPool(ComponentRegistry componentRegistry, List<Class<? extends CleanComponent>> components, int initSize) {
        Preconditions.checkArgument(initSize >= 0 && initSize <= PoolCapacity.MAX_CAPACITY, "Invalid initial pool capacity.");
        this.componentRegistry = componentRegistry;
        this.components = ObjectArrayView.copyOf(components,
            size -> (Class<? extends CleanComponent>[]) new Class[size]);
        this.initSize = initSize;
        currentSize = initSize;
    }

    void trimCapacity() {
    }

    void reserveCapacity(int capacity) {
        throw new UnsupportedOperationException("This pool does not support capacity reservation.");
    }

    public abstract boolean containsEntity(int entityID);

    /**
     * Resolves a committed entity to its storage row.
     *
     * @param entityID ID of an entity present in this pool.
     * @return The current row index.
     * @throws IllegalArgumentException If the entity is absent.
     */
    abstract int getEntityRow(int entityID);

    public void beginExecution() {
    }

    public void endExecution() {
    }

    /**
     * Reconstructs an independent copy from the entity's current row.
     *
     * @param entityID  ID of an entity present in this pool.
     * @param component Exact component class in this archetype.
     * @return A copy of the stored component.
     */
    @NonNull
    public abstract CleanComponent getComponent(int entityID, Class<? extends CleanComponent> component);

    /**
     * Writes a component value synchronously into an existing row.
     *
     * @param entityID  ID of an entity present in this pool.
     * @param component Value matching the schema for a component in this archetype. Keep it stable during the call.
     */
    public abstract void setComponent(int entityID, CleanComponent component);

    /**
     * Publishes a new row after writing its component values.
     *
     * @param entityID   ID absent from this pool.
     * @param components One valid value per component type in any order. The caller may reuse the array and values after return.
     * @throws IllegalArgumentException If the ID is already present or the values do not match the composition.
     * @throws IllegalStateException    If an active execution protects this pool.
     */
    public abstract void addEntity(int entityID, CleanComponent[] components);

    /**
     * Removes a committed entity and makes its row available for reuse.
     *
     * @param entityID ID of an entity present in this pool.
     * @throws IllegalStateException If an active execution protects this pool.
     */
    public abstract void removeEntity(int entityID);

    /**
     * Publishes an entity in the destination while the caller still owns the source row.
     *
     * @param entityID ID present in the source and absent from the destination.
     * @param source   Pool from the same registry, with structural changes allowed.
     * @param addition Valid payload for the added type, or null for a removal.
     */
    abstract void addEntityFrom(int entityID, ArchetypeDataPool source, @Nullable CleanComponent addition);

    /**
     * Borrows the column at a path resolved against this pool's schema.
     *
     * @param component        Exact component class in the archetype.
     * @param fieldAccessChain Nonempty field path ending at a primitive leaf.
     * @return The live column. Use it under the current execution scope or on the manager thread during commit.
     */
    @NonNull
    public abstract PrimitiveArray getArray(Class<? extends CleanComponent> component, String... fieldAccessChain);

    /**
     * Borrows a column using resolved metadata.
     *
     * @param component Exact component class in the archetype.
     * @param column    Column belonging to that component's registered layout.
     * @return The live column, with the same lifetime as the path-based lookup.
     */
    public PrimitiveArray getArray(Class<? extends CleanComponent> component, SchemaLayout.Column column) {
        return getArray(component, column.path().elements());
    }

    /**
     * Borrows row occupancy metadata.
     *
     * @return Pool-owned metadata stable while this pool is guarded.
     */
    @NonNull
    public abstract PoolRows getRows();

    /**
     * Finds the entity ID at an occupied row.
     *
     * @param index Row index in this pool.
     * @return The manager-local entity ID.
     * @throws IllegalStateException If the row is unoccupied.
     */
    public abstract int getEntityID(int index);

    public abstract String getSnapshot();
}
