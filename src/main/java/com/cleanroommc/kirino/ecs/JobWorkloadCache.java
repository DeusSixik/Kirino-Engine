package com.cleanroommc.kirino.ecs;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Retains one reusable preparation buffer per executor. Overlapping launches acquire separate instances.
 */
final class JobWorkloadCache {
    /**
     * Maximum estimate count retained after a launch returns the buffer.
     */
    static final int MAX_RETAINED_ESTIMATES = 1 << 16;

    private final AtomicReference<JobWorkload> available = new AtomicReference<>(new JobWorkload());

    /**
     * Acquires exclusive use of a preparation buffer.
     *
     * @return A retained or newly allocated empty workload.
     */
    JobWorkload acquire() {
        JobWorkload workload = available.getAndSet(null);
        return workload != null ? workload : new JobWorkload();
    }

    /**
     * Clears estimate state and attempts to retain the reusable buffer.
     *
     * @param workload Buffer no longer used by its submitting launch.
     */
    void release(JobWorkload workload) {
        workload.reset(MAX_RETAINED_ESTIMATES);
        available.compareAndSet(null, workload);
    }

    /**
     * Drops cached scratch after runtime work has drained.
     */
    void clear() {
        available.set(null);
    }
}
