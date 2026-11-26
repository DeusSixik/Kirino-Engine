package com.cleanroommc.kirino.ecs.api.system;

import com.cleanroommc.kirino.ecs.api.world.CleanWorld;
import com.cleanroommc.kirino.ecs.api.EntityManager;
import com.cleanroommc.kirino.ecs.api.job.JobScheduler;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SystemExeFlowGraph;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SystemExeFlowGraph.Builder;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SingleFlow;
import com.cleanroommc.kirino.utils.ReflectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.jspecify.annotations.NonNull;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * A system updated by an execution flow.
 *
 * <p>A managed run holds exclusive access to the system instance until tracked work
 * and terminal callbacks finish. Order direct calls separately if you invoke
 * {@link #update(EntityManager, JobScheduler)} outside a flow.</p>
 */
public abstract class CleanSystem {
    /**
     * Registers external work associated with this system's update.
     */
    protected final @NonNull ExecutionContainer execution = new ExecutionContainer();

    /**
     * Submits or performs the work for one system update.
     *
     * <p>The flow automatically tracks jobs launched on this thread during the call.
     * Register other work through {@link #execution} before returning. The flow waits
     * for tracked work before visiting dependent barriers, including when update throws.</p>
     *
     * @param entityManager Entity manager bound to the flow.
     * @param jobScheduler  Job scheduler bound to the flow.
     */
    public abstract void update(@NonNull EntityManager entityManager, @NonNull JobScheduler jobScheduler);

    /**
     * Creates a single-system flow builder using explicit ECS services.
     *
     * @param entityManager Manager used during system updates.
     * @param jobScheduler  Scheduler used during system updates. Pair it with the manager of the same runtime.
     * @param type          Exact type of the system instance added to the builder.
     * @param <T>           System type.
     * @return A new mutable builder.
     * @throws NullPointerException If any argument is null.
     */
    @NonNull
    public static <T extends CleanSystem> Builder<SingleFlow<T>> newSingleFlowBuilder(
        @NonNull EntityManager entityManager, @NonNull JobScheduler jobScheduler, @NonNull Class<T> type) {

        Preconditions.checkNotNull(entityManager);
        Preconditions.checkNotNull(jobScheduler);
        Preconditions.checkNotNull(type);

        return MethodHolder.newBuilder(entityManager, jobScheduler, type);
    }

    private static final class MethodHolder {
        private static final Function<CleanSystem, Object> STATE = system -> system.execution.state();
        private static final MethodHandle FACTORY;

        static {
            try {
                Class<?> implementation = Class.forName("com.cleanroommc.kirino.ecs.SingleFlowExecutor");
                FACTORY = Preconditions.checkNotNull(ReflectionUtils.getMethod(implementation, "newBuilder",
                    SystemExeFlowGraph.Builder.class, EntityManager.class, JobScheduler.class, Class.class, Function.class));
            } catch (ClassNotFoundException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        @SuppressWarnings("unchecked")
        static <T extends CleanSystem> SystemExeFlowGraph.Builder<SingleFlow<T>> newBuilder(
            EntityManager entities, JobScheduler scheduler, Class<T> type) {
            try {
                return (SystemExeFlowGraph.Builder<SingleFlow<T>>) FACTORY.invokeExact(entities, scheduler, (Class) type, STATE);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }
    }
}
