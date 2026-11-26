package com.cleanroommc.kirino.ecs.api;

import com.cleanroommc.kirino.ecs.api.job.JobScheduler;
import com.cleanroommc.kirino.utils.ReflectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;

/**
 * Owns one ECS session and coordinates its entity manager and job scheduler.
 *
 * <p>A runtime starts in {@link State#RUNNING}. Quiescing closes admission for new
 * work and drains accepted jobs and flows. Reset and close both wait for this drain
 * before cleaning up on the calling thread.</p>
 */
public final class CleanECSRuntime implements AutoCloseable {
    /**
     * Describes admission and cleanup state for this runtime.
     */
    public enum State {
        /**
         * New jobs, commands, and flows may be admitted.
         */
        RUNNING,
        /**
         * Admission is closed while accepted work is draining.
         */
        QUIESCING,
        /**
         * Accepted work has drained and cleanup may finish on the caller thread.
         */
        QUIESCENT,
        /**
         * The runtime cannot be used again.
         */
        CLOSED
    }

    /**
     * Public entity facade belonging to this runtime.
     */
    public final @NonNull EntityManager entityManager;
    /**
     * Public job facade belonging to this runtime.
     */
    public final @NonNull JobScheduler jobScheduler;

    private CleanECSRuntime(EntityManager entityManager, JobScheduler jobScheduler) {
        this.entityManager = Preconditions.checkNotNull(entityManager);
        this.jobScheduler = Preconditions.checkNotNull(jobScheduler);
    }

    /**
     * Creates a runtime and discovers its component, struct, and job definitions.
     *
     * @param eventBus Forge event bus used to request registrations.
     * @param logger   Logger used during bootstrap.
     * @return A running runtime.
     * @throws NullPointerException If either argument is null.
     * @throws RuntimeException     If bootstrap fails.
     */
    @NonNull
    public static CleanECSRuntime create(@NonNull EventBus eventBus, @NonNull Logger logger) {
        return MethodHolder.create(Preconditions.checkNotNull(eventBus), Preconditions.checkNotNull(logger));
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return The state observed during this call.
     */
    @NonNull
    public State state() {
        return MethodHolder.state(entityManager);
    }

    /**
     * Creates an empty session sharing the registered schemas and job definitions.
     *
     * @return A running session with independent entities, scheduler, and lifecycle.
     * @throws IllegalStateException If this manager is unavailable or the call reenters a lifecycle callback.
     */
    @NonNull
    public CleanECSRuntime newSession() {
        return MethodHolder.newSession(entityManager, jobScheduler);
    }

    /**
     * Closes admission and returns an observer for the drain of accepted work.
     *
     * <p>The runtime reaches {@link State#QUIESCENT} when accepted work finishes, even if
     * that work fails. Cancelling the observer does not stop the work or release its guards.
     * A running flow may fail at its next barrier when it detects closed admission.
     * Pending commands remain queued until cleanup discards them.</p>
     *
     * @return An observer that reports successful or exceptional completion of the drain.
     * @throws IllegalStateException If called from a lifecycle callback on this manager.
     */
    @NonNull
    public CompletableFuture<Void> quiesce() {
        return MethodHolder.quiesce(entityManager);
    }

    /**
     * Drains accepted work, clears entities, and opens a new lifecycle epoch.
     *
     * <p>Pending commands are discarded. Committed entities receive their remaining destroy
     * callbacks on the calling thread. Old handles and flows expire, and registrations are
     * retained. A cleanup failure leaves the runtime closed. A failure from drained work can
     * be reported after successful cleanup has already reopened the runtime.</p>
     *
     * @throws IllegalStateException If called from this runtime's execution or callback, or if the runtime cannot be reset.
     * @throws RuntimeException      If accepted work or cleanup fails.
     */
    public void reset() {
        MethodHolder.finish(entityManager, true);
    }

    /**
     * Permanently closes this runtime after draining accepted work.
     *
     * <p>Discards pending commands and invokes remaining destroy callbacks for committed
     * entities on the calling thread. Cleanup continues past individual callback failures
     * and reports their combined result.</p>
     *
     * @throws IllegalStateException If called from this runtime's execution or callback.
     * @throws RuntimeException      If accepted work or cleanup fails.
     */
    @Override
    public void close() {
        MethodHolder.finish(entityManager, false);
    }

    private static final class MethodHolder {
        private static final Delegate DELEGATE;

        static {
            try {
                Class<?> bootstrap = Class.forName("com.cleanroommc.kirino.ecs.RuntimeBootstrap");
                DELEGATE = new Delegate(
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(bootstrap, "create", CleanECSRuntime.class, EventBus.class, Logger.class)),
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(bootstrap, "state", State.class, EntityManager.class)),
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(bootstrap, "quiesce", CompletableFuture.class, EntityManager.class)),
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(bootstrap, "finish", void.class, EntityManager.class, boolean.class)),
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(bootstrap, "newSession", CleanECSRuntime.class, EntityManager.class, JobScheduler.class)));
            } catch (ClassNotFoundException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        private static CleanECSRuntime create(EventBus events, Logger logger) {
            try {
                return (CleanECSRuntime) DELEGATE.create.invokeExact(events, logger);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        static State state(EntityManager manager) {
            try {
                return (State) DELEGATE.state.invokeExact(manager);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        static CleanECSRuntime newSession(EntityManager manager, JobScheduler scheduler) {
            try {
                return (CleanECSRuntime) DELEGATE.newSession.invokeExact(manager, scheduler);
            } catch (Throwable cause) {
                Throwables.throwIfUnchecked(cause);
                throw new CompletionException(cause);
            }
        }

        @SuppressWarnings("unchecked")
        static CompletableFuture<Void> quiesce(EntityManager manager) {
            try {
                return (CompletableFuture<Void>) DELEGATE.quiesce.invokeExact(manager);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        static void finish(EntityManager manager, boolean reset) {
            try {
                DELEGATE.finish.invokeExact(manager, reset);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        private record Delegate(MethodHandle create, MethodHandle state, MethodHandle quiesce, MethodHandle finish,
                                MethodHandle newSession) {
        }
    }
}
