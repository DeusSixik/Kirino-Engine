package com.cleanroommc.kirino.ecs.api.world;

import com.cleanroommc.kirino.ecs.api.system.exegraph.SingleFlow;
import com.cleanroommc.kirino.ecs.api.EntityManager;
import com.cleanroommc.kirino.ecs.api.job.JobScheduler;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SystemExeFlowGraph;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SystemExeFlowGraph.Builder;
import com.cleanroommc.kirino.ecs.api.system.CleanSystem;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.NonNull;

/**
 * Base class for a world that coordinates ECS updates and structural commits.
 */
public abstract class CleanWorld {
    /**
     * Entity manager used by the world and its flows.
     */
    protected final @NonNull EntityManager entityManager;
    /**
     * Scheduler used by the world and its flows.
     */
    protected final @NonNull JobScheduler jobScheduler;

    /**
     * Binds the services used for world updates.
     *
     * @param entityManager Entity manager for this world.
     * @param jobScheduler  Scheduler for the same ECS session.
     * @throws NullPointerException If either service is null.
     */
    public CleanWorld(@NonNull EntityManager entityManager, @NonNull JobScheduler jobScheduler) {
        this.entityManager = Preconditions.checkNotNull(entityManager);
        this.jobScheduler = Preconditions.checkNotNull(jobScheduler);
    }

    /**
     * Commits deferred commands through {@link EntityManager#flush()}.
     */
    protected final void flushECS() {
        entityManager.flush();
    }

    /**
     * Creates a single-system builder bound to this world's ECS services.
     *
     * @param type Exact type of the system instance added to the builder.
     * @param <T>  System type.
     * @return A new mutable builder.
     * @throws NullPointerException If the type is null.
     */
    @NonNull
    public final <T extends CleanSystem> Builder<SingleFlow<T>> newSingleFlowBuilder(@NonNull Class<T> type) {
        return CleanSystem.newSingleFlowBuilder(entityManager, jobScheduler, type);
    }

    /**
     * Advances the world's application-defined update.
     */
    public abstract void update();
}
