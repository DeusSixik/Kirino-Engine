package com.cleanroommc.kirino.ecs.api.system.exegraph;

import com.cleanroommc.kirino.ecs.api.system.CleanSystem;
import com.cleanroommc.kirino.ecs.api.world.CleanWorld;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Executes a barrier graph containing exactly one system transition.
 *
 * @param <TSys> Exact system type configured by the builder.
 */
public final class SingleFlow<TSys extends CleanSystem> implements SystemExeFlowGraph {
    /**
     * System instance bound to the single transition.
     */
    private final TSys system;
    /**
     * Kernel graph that owns execution state and the topology snapshot.
     */
    private final SystemExeFlowGraph execution;

    private SingleFlow(TSys system, SystemExeFlowGraph execution) {
        this.system = Preconditions.checkNotNull(system);
        this.execution = Preconditions.checkNotNull(execution);
    }

    /**
     * Returns the system instance supplied to the builder.
     *
     * @return The bound system. Coordinate access to its mutable state with managed execution.
     */
    @NonNull
    public TSys getSystem() {
        return system;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() {
        execution.execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public CompletableFuture<Void> executeAsync(@NonNull Executor executor) {
        return execution.executeAsync(executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExecuting() {
        return execution.isExecuting();
    }

    /**
     * Creates a builder using the supplied world's ECS services.
     *
     * @param world World supplying the manager and scheduler.
     * @param clazz Exact type of the system instance added to the builder.
     * @param <T>   System type.
     * @return A new mutable builder.
     * @throws NullPointerException If the world or class is null.
     */
    @NonNull
    public static <T extends CleanSystem> Builder<SingleFlow<T>> newBuilder(@NonNull CleanWorld world, @NonNull Class<T> clazz) {
        return Preconditions.checkNotNull(world).newSingleFlowBuilder(Preconditions.checkNotNull(clazz));
    }
}
