package com.cleanroommc.kirino.ecs.api.job;

import com.cleanroommc.kirino.ecs.api.EntityCommands;
import com.cleanroommc.kirino.ecs.api.EntityQuery;
import org.jspecify.annotations.NonNull;

/**
 * Processes occupied rows of the archetypes selected for one scheduler launch.
 *
 * <p>A registered job class must be concrete and have a public no-argument constructor.
 * The scheduler uses one instance to declare the query, binds a separate instance for
 * workload estimation in each selected pool, and creates a separate bound instance for
 * each asynchronous partition. Partitions may run on the same worker thread.</p>
 *
 * @see JobDataQuery
 * @see JobExternalDataQuery
 */
public interface ParallelJob {
    /**
     * Processes one occupied row with the job's injected data.
     *
     * @param commands      Deferred command writer for this launch.
     * @param index         Storage row within the current archetype. Use it to index bound columns.
     * @param entityID      Manager-local identifier for commands during this launch.
     * @param threadOrdinal Zero-based partition ordinal within the launch.
     */
    void execute(@NonNull EntityCommands commands, int index, int entityID, int threadOrdinal);

    /**
     * Declares the component composition required by this job before bindings are injected.
     *
     * <p>Require every component referenced by {@link JobDataQuery} so each matching
     * archetype supplies the requested columns.</p>
     *
     * @param entityQuery Mutable filter for this launch.
     */
    void query(@NonNull EntityQuery entityQuery);

    /**
     * Estimates the relative work for one occupied row using the injected data.
     *
     * <p>The scheduler evaluates each occupied row once per launch before partitioning
     * its pool. A bound worker instance may differ from the estimator instance. Keep
     * estimation free of side effects that execution would need to observe.</p>
     *
     * @param index Storage row within the current archetype.
     * @return A positive workload weight. The units are relative to other rows.
     */
    int estimateWorkload(int index);
}
