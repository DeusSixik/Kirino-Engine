package com.cleanroommc.kirino.ecs.api.job;

import com.cleanroommc.kirino.ecs.api.EntityManager;
import com.cleanroommc.kirino.utils.ReflectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Launches registered jobs against the committed storage of one ECS session.
 *
 * <p>The scheduler keeps selected pools structurally stable until accepted tasks finish.
 * Different launches may share columns or external objects. The caller must order
 * overlapping reads and writes or synchronize them externally. See
 * {@link com.cleanroommc.kirino.ecs.api.storage.PrimitiveArray} for column lifetime.</p>
 */
public final class JobScheduler {
    private final Object executor;

    private JobScheduler(Object executor) {
        this.executor = Preconditions.checkNotNull(executor);
    }

    /**
     * Reports the completion and partitioning of one accepted job launch.
     *
     * @param future           Observer of execution and storage release for a scheduler-created handle.
     * @param totalThreadCount Number of partitions, including inline partitions.
     * @param async            Whether any partition was submitted to the executor.
     */
    public record ExecutionHandle(@NonNull CompletableFuture<Void> future, int totalThreadCount, boolean async) {
        /**
         * Creates a handle for an execution with a completion future.
         *
         * @param future           Completion future.
         * @param totalThreadCount Partition count.
         * @param async            Whether execution used asynchronous submission.
         * @throws NullPointerException If the future is null.
         */
        public ExecutionHandle {
            Preconditions.checkNotNull(future);
        }
    }

    /**
     * Launches a job over the occupied rows of matching committed archetypes.
     *
     * <p>Query, binding, estimation, and small workloads run on the calling thread.
     * A failure during preparation, inline work, or executor submission is thrown directly.
     * If earlier submissions were accepted, the scheduler holds their storage guards until
     * they finish. Worker failures are reported through the returned future when a handle is
     * returned. Writes and accepted commands can survive a failed launch.</p>
     *
     * <p>External bindings capture references during preparation. Keep the map stable during
     * the call and synchronize access to any shared mutable values. Cancellation of a returned
     * future does not stop accepted tasks or release their guards early.</p>
     *
     * @param entityManager Manager belonging to this scheduler's session.
     * @param job           Registered concrete job class.
     * @param externalData  Values keyed by {@link JobExternalDataQuery}. May be null when the job has no external bindings.
     * @param workers       Executor for parallel partitions. It must eventually run each accepted task.
     * @return A handle that observes completion after accepted work releases its storage guards.
     * @throws NullPointerException     If the manager, job class, or executor is null.
     * @throws IllegalArgumentException If the manager is foreign or external bindings are missing or incompatible.
     * @throws IllegalStateException    If the session cannot admit work or the job is unregistered.
     */
    @NonNull
    public ExecutionHandle executeParallelJob(@NonNull EntityManager entityManager,
                                              @NonNull Class<? extends ParallelJob> job,
                                              @Nullable Map<@NonNull String, @Nullable Object> externalData,
                                              @NonNull Executor workers) {
        Preconditions.checkNotNull(entityManager);
        Preconditions.checkNotNull(job);
        Preconditions.checkNotNull(workers);

        return MethodHolder.execute(executor, entityManager, job, externalData, workers);
    }

    private static final class MethodHolder {
        private static final Delegate DELEGATE;

        static {
            try {
                Class<?> implementation = Class.forName("com.cleanroommc.kirino.ecs.JobExecutor");
                MethodHandle execute = Preconditions.checkNotNull(ReflectionUtils.getMethod(implementation,
                    "executeParallelJob", ExecutionHandle.class, EntityManager.class, Class.class, Map.class, Executor.class));
                DELEGATE = new Delegate(execute.asType(MethodType.methodType(ExecutionHandle.class,
                    Object.class, EntityManager.class, Class.class, Map.class, Executor.class)));
            } catch (ClassNotFoundException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        private static ExecutionHandle execute(Object executor, EntityManager entities, Class<? extends ParallelJob> job,
                                               Map<String, Object> externalData, Executor workers) {
            try {
                return (ExecutionHandle) DELEGATE.execute.invokeExact(executor, entities, job, externalData, workers);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        private record Delegate(MethodHandle execute) {
        }
    }
}
