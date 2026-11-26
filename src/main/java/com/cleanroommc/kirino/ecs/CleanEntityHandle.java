package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Binds public handle operations to one manager, slot index, and generation.
 */
final class CleanEntityHandle implements com.cleanroommc.kirino.ecs.api.CleanEntityHandle {
    private final EntityManager entityManager;
    final int index;
    final int generation;

    CleanEntityHandle(EntityManager entityManager, int index, int generation) {
        this.entityManager = entityManager;
        this.index = index;
        this.generation = generation;
    }

    /**
     * {@inheritDoc}
     */
    public boolean valid() {
        return entityManager.isValid(index, generation);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAlive() {
        return entityManager.isAlive(index, generation);
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(@NonNull Class<? extends CleanComponent> component) {
        return entityManager.contains(index, generation, component);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public <T extends CleanComponent> Optional<T> readComponent(@NonNull Class<T> component) {
        return entityManager.readComponent(index, generation, component);
    }

    /**
     * {@inheritDoc}
     */
    public boolean tryDestroy() {
        return entityManager.tryDestroy(index, generation);
    }

    /**
     * {@inheritDoc}
     */
    public boolean trySetComponent(@NonNull CleanComponent component) {
        return entityManager.trySetComponent(index, generation, component);
    }

    /**
     * {@inheritDoc}
     */
    public boolean tryAddComponent(@NonNull CleanComponent component) {
        return entityManager.tryAddComponent(index, generation, component);
    }

    /**
     * {@inheritDoc}
     */
    public boolean tryRemoveComponent(@NonNull Class<? extends CleanComponent> component) {
        return entityManager.tryRemoveComponent(index, generation, component);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CleanEntityHandle that)) {
            return false;
        }

        return index == that.index && generation == that.generation && entityManager == that.entityManager;
    }

    @Override
    public int hashCode() {
        int hash = System.identityHashCode(entityManager);
        hash = 31 * hash + index;
        return 31 * hash + generation;
    }
}
