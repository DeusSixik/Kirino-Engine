package com.cleanroommc.kirino.ecs.api.system;

import com.cleanroommc.kirino.ecs.api.job.JobScheduler;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SystemExeFlowGraph;
import com.cleanroommc.kirino.utils.ReflectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Tracks work a system update must finish before its flow advances.
 *
 * <p>Calls made during a managed update append work and deduplicate it by completion
 * identity. Only the update thread may register during that run. Calls outside a
 * managed run replace the prior manual registration. Arrays are consumed during the call.
 * For externally supplied futures, the caller must ensure completion represents actual
 * work ending and that no task continues to access ECS data afterward.</p>
 */
public final class ExecutionContainer {
    ExecutionContainer() {
    }

    /**
     * Opaque kernel state shared with the owning system's flow.
     */
    private final Object state = MethodHolder.create();

    Object state() {
        return state;
    }

    /**
     * Registers an empty set of tasks. During a managed update, it leaves already tracked work in place.
     *
     * @throws IllegalStateException If called from outside the thread running the current managed update.
     */
    public void noExecutions() {
        updateExecutions(null, (JobScheduler.ExecutionHandle[]) null);
    }

    /**
     * Registers the actual completions represented by job handles.
     *
     * @param handles Non-null handles to track.
     * @throws NullPointerException  If the array or an element is null.
     * @throws IllegalStateException If called from outside the thread running the current managed update.
     */
    public void updateExecutions(JobScheduler.@NonNull ExecutionHandle @NonNull ... handles) {
        updateExecutions(null, Preconditions.checkNotNull(handles));
    }

    /**
     * Registers external futures and job handles.
     *
     * @param futures External futures to track, or null for none. Each future must represent the full lifetime of its work.
     * @param handles Job handles to track, or null for none.
     * @throws NullPointerException  If either array contains a null element.
     * @throws IllegalStateException If called from outside the thread running the current managed update.
     */
    public void updateExecutions(@NonNull CompletableFuture<Void> @Nullable [] futures,
                                 JobScheduler.@NonNull ExecutionHandle @Nullable ... handles) {
        MethodHolder.register(state, futures, handles);
    }

    private static final class MethodHolder {
        private static final Delegate DELEGATE;

        static {
            try {
                Class<?> implementation = Class.forName("com.cleanroommc.kirino.ecs.SystemExecution");
                DELEGATE = new Delegate(
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(implementation, "create", Object.class)),
                    Preconditions.checkNotNull(ReflectionUtils.getMethod(implementation, "register", void.class,
                        Object.class, CompletableFuture[].class, JobScheduler.ExecutionHandle[].class)));
            } catch (ClassNotFoundException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        static Object create() {
            try {
                return (Object) DELEGATE.create.invokeExact();
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        static void register(Object state, CompletableFuture<Void>[] futures, JobScheduler.ExecutionHandle[] handles) {
            try {
                DELEGATE.register.invokeExact(state, (CompletableFuture[]) futures, handles);
            } catch (Throwable failure) {
                Throwables.throwIfUnchecked(failure);
                throw new CompletionException(failure);
            }
        }

        private record Delegate(MethodHandle create, MethodHandle register) {
        }
    }
}
