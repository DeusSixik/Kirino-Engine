package com.cleanroommc.kirino.ecs.api;

import com.cleanroommc.anone.value.CanIgnoreReturnValue;
import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.jspecify.annotations.NonNull;

/**
 * Builds a component-composition filter for the job scheduler.
 *
 * <p>An archetype matches when it includes every required type and excludes every forbidden
 * type. An empty query accepts all archetypes. Duplicate conditions have no effect. Requiring
 * and excluding the same type produces no matches. Types are compared by class identity.</p>
 *
 * <p>Configure the query on one thread. The scheduler selects committed pools before
 * execution. Subsequent structural commands do not change that selection.</p>
 */
public final class EntityQuery {
    /**
     * Component classes that every match must contain.
     */
    private final ReferenceSet<Class<? extends CleanComponent>> mustHave;
    /**
     * Component classes excluded from all matches.
     */
    private final ReferenceSet<Class<? extends CleanComponent>> mustNotHave;

    private EntityQuery() {
        mustHave = new ReferenceArraySet<>();
        mustNotHave = new ReferenceArraySet<>();
    }

    /**
     * Creates a query without composition restrictions.
     *
     * @return An empty mutable query.
     */
    @NonNull
    public static EntityQuery query() {
        return new EntityQuery();
    }

    /**
     * Requires a component on matching entities.
     *
     * @param component Exact component class to match.
     * @return This query for chaining.
     * @throws NullPointerException If the class is null.
     */
    @CanIgnoreReturnValue
    @NonNull
    public EntityQuery with(@NonNull Class<? extends CleanComponent> component) {
        Preconditions.checkNotNull(component);

        mustHave.add(component);
        return this;
    }

    /**
     * Excludes entities with the specified component.
     *
     * @param component Exact component class to match.
     * @return This query for chaining.
     * @throws NullPointerException If the class is null.
     */
    @CanIgnoreReturnValue
    @NonNull
    public EntityQuery without(@NonNull Class<? extends CleanComponent> component) {
        Preconditions.checkNotNull(component);

        mustNotHave.add(component);
        return this;
    }
}
