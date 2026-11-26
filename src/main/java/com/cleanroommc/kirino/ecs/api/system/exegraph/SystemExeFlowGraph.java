package com.cleanroommc.kirino.ecs.api.system.exegraph;

import com.cleanroommc.anone.value.CanIgnoreReturnValue;
import com.cleanroommc.kirino.ecs.api.system.CleanSystem;
import com.cleanroommc.kirino.ecs.api.world.CleanWorld;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Orders system updates and callbacks through a directed acyclic graph of barriers.
 *
 * <p>A barrier waits for the work tracked by incoming transitions before invoking its callback
 * and starting outgoing transitions. A built flow snapshots its topology and callback
 * references. The same flow rejects overlapping runs.</p>
 */
public interface SystemExeFlowGraph {
    /**
     * Reserved identifier of the initial barrier.
     */
    String START_NODE = "__START__";
    /**
     * Reserved identifier of the terminal barrier.
     */
    String END_NODE = "__END__";

    /**
     * Runs the graph on the caller's thread and waits for tracked work.
     *
     * <p>On failure, the flow stops advancing through barriers and drains work already
     * started before reporting the failure. Writes and callback side effects can survive.</p>
     *
     * @throws IllegalStateException If this flow is busy, its system is already managed by another flow, or its runtime epoch is unavailable.
     * @throws RuntimeException      If a system, tracked task, or callback fails.
     */
    void execute();

    /**
     * Dispatches the graph and its barrier visits through an executor.
     *
     * <p>Terminal callbacks can run on the thread that completes tracked work. Cancellation
     * of the returned observer affects only observation. Keep the executor available until
     * accepted graph work finishes.</p>
     *
     * @param executor Executor for this flow and its barrier visits.
     * @return An observer that completes after tracked work and terminal callbacks finish.
     * @throws NullPointerException       If the executor is null.
     * @throws IllegalStateException      If the flow is busy or its runtime epoch is unavailable.
     * @throws RejectedExecutionException If the initial submission is rejected.
     */
    @NonNull
    CompletableFuture<Void> executeAsync(@NonNull Executor executor);

    /**
     * Reports whether this flow still holds its execution admission.
     *
     * @return Whether a run is in progress. Observe the execution result to determine success.
     */
    boolean isExecuting();

    /**
     * Configures a flow whose nodes are reachable from {@link SystemExeFlowGraph#START_NODE}
     * and can reach {@link SystemExeFlowGraph#END_NODE}.
     *
     * @param <TFlowGraph> Type of flow constructed by this builder.
     */
    interface Builder<TFlowGraph extends SystemExeFlowGraph> {
        /**
         * Adds a barrier or replaces the callback of an existing custom barrier.
         *
         * @param nodeID   Non-null identifier distinct from the reserved start and end identifiers.
         * @param callback Action invoked after incoming work succeeds, or null for no action.
         * @return This builder.
         * @throws IllegalArgumentException If the identifier is reserved.
         * @throws NullPointerException     If the identifier is null.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> addBarrierNode(@NonNull String nodeID, @Nullable Runnable callback);

        /**
         * Adds an ordering edge that launches no system.
         *
         * @param fromNodeID Identifier of an existing source barrier.
         * @param toNodeID   Identifier of an existing destination barrier.
         * @return This builder.
         * @throws NullPointerException If either identifier is null or unknown.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> addDummyTransition(@NonNull String fromNodeID, @NonNull String toNodeID);

        /**
         * Adds an edge that updates a system and tracks its work before the destination barrier.
         *
         * @param task       System instance. A single-flow builder accepts one edge with the exact configured system type.
         * @param fromNodeID Identifier of an existing source barrier.
         * @param toNodeID   Identifier of an existing destination barrier.
         * @return This builder.
         * @throws NullPointerException     If the system is null or a barrier identifier is null or unknown.
         * @throws IllegalArgumentException If the system has the wrong type for the builder.
         * @throws IllegalStateException    If a single-flow builder already has a system transition.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> addTransition(@NonNull CleanSystem task, @NonNull String fromNodeID, @NonNull String toNodeID);

        /**
         * Sets the action that runs at the start barrier before outgoing transitions.
         *
         * @param callback Start action, or null to clear it.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> setStartNodeCallback(@Nullable Runnable callback);

        /**
         * Sets the action that runs at the end barrier after its dependencies succeed.
         *
         * @param callback End action, or null to clear it.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> setEndNodeCallback(@Nullable Runnable callback);

        /**
         * Sets the terminal success action after the end barrier and tracked work succeed.
         *
         * @param callback Success action, or null to clear it.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> setFinishCallback(@Nullable Runnable callback);

        /**
         * Sets the terminal failure action once started work has drained.
         *
         * <p>Failures thrown by the failure action are combined with the original failure.
         * A rejected initial submission is thrown at the call site.</p>
         *
         * @param callback Consumer of the execution failure, or null to clear it.
         * @return This builder.
         */
        @CanIgnoreReturnValue
        @NonNull
        Builder<TFlowGraph> setFailureCallback(@Nullable Consumer<Throwable> callback);

        /**
         * Validates and snapshots the graph and callback references.
         *
         * @return A flow independent of subsequent builder edits.
         * @throws IllegalStateException If the topology is invalid or the required system transition is missing.
         */
        @NonNull
        TFlowGraph build();
    }

}
