package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Identifies an exact set of component classes by class identity.
 *
 * <p>The key snapshots its inputs. Hashing sums each class's identity hash with
 * Java int wraparound so order has no effect. Equality verifies membership even
 * when hashes collide. Small compositions use a linear membership check.</p>
 */
final class ArchetypeKey {
    private static final int LINEAR_LIMIT = 8;
    private final Class<?>[] components;
    private final ReferenceOpenHashSet<Class<?>> membership;
    private final int hash;

    /**
     * Creates a key for a composition.
     *
     * @param componentTypes Component classes in any order, with no duplicates.
     * @throws NullPointerException     If the list or an element is null.
     * @throws IllegalArgumentException If a component class occurs more than once.
     */
    public ArchetypeKey(List<Class<? extends CleanComponent>> componentTypes) {
        Preconditions.checkNotNull(componentTypes);

        components = componentTypes.toArray(Class<?>[]::new);
        membership = components.length > LINEAR_LIMIT ? new ReferenceOpenHashSet<>(components.length) : null;
        int identityHash = 0;
        for (int i = 0; i < components.length; i++) {
            Class<?> component = Preconditions.checkNotNull(components[i]);

            if (membership != null) {
                if (!membership.add(component)) {
                    throw new IllegalArgumentException("Duplicate component type " + component + ".");
                }
            } else {
                for (int previous = 0; previous < i; previous++) {
                    if (components[previous] == component) {
                        throw new IllegalArgumentException("Duplicate component type " + component + ".");
                    }
                }
            }

            identityHash += System.identityHashCode(component);
        }
        hash = identityHash;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArchetypeKey that) || components.length != that.components.length) {
            return false;
        }

        for (Class<?> component : components) {
            if (!that.containsType(component)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        String[] names = new String[components.length];
        for (int i = 0; i < components.length; i++) {
            names[i] = components[i].getName();
        }
        Arrays.sort(names);
        return "ArchetypeKey{" + String.join(",", names) + "}";
    }

    public boolean contains(Class<? extends CleanComponent> component) {
        return containsType(Preconditions.checkNotNull(component));
    }

    private boolean containsType(Class<?> component) {
        if (membership != null) {
            return membership.contains(component);
        }

        for (Class<?> stored : components) {
            if (stored == component) {
                return true;
            }
        }

        return false;
    }
}
