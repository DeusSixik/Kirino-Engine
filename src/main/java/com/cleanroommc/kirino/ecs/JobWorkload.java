package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.job.ParallelJob;

import java.util.Arrays;

/**
 * Prepares positive load weights for occupied rows in one pool.
 *
 * <p>A uniform pool uses a single weight. A mixed pool uses densely packed estimates
 * in occupied-row order. Holes consume no entry. Preparation scratch is held by the
 * submitting thread, and workers capture their row bounds before submission.</p>
 */
final class JobWorkload {
    private static final int[] NO_ESTIMATES = new int[0];

    /**
     * Common positive weight, or zero if estimates are mixed or no rows are occupied.
     */
    int uniform;
    /**
     * Reusable dense estimates. Read only the live prefix when weights are mixed.
     */
    int[] estimates = NO_ESTIMATES;

    void reset(int maximumRetainedEstimates) {
        uniform = 0;
        if (estimates.length > maximumRetainedEstimates) {
            estimates = NO_ESTIMATES;
        }
    }

    /**
     * Estimates each occupied row once before choosing partition boundaries.
     *
     * <p>The returned load is the sum of per-row weights. A long holds the sum of at most
     * an int-sized number of int weights without overflow.</p>
     *
     * @param job  Bound estimator for this pool.
     * @param rows Occupancy metadata held stable by the execution scope.
     * @return Total relative workload, or zero if there are no occupied rows.
     * @throws IllegalStateException If an estimator reports a nonpositive weight.
     */
    long prepare(ParallelJob job, PoolRows rows) {
        int liveCount = 0;
        long total = 0;
        int common = 0;
        int rowLimit = rows.limit;
        int[] nextFree = rows.nextFree;
        for (int row = 0; row < rowLimit; row++) {
            if (nextFree[row] != 0) {
                continue;
            }

            int estimate = estimate(job, row);
            if (estimate != common) {
                if (liveCount == 0) {
                    common = estimate;
                } else {
                    uniform = 0;
                    return total + prepareMixed(job, rows, row, liveCount, common, estimate);
                }
            }
            liveCount++;
            total += estimate;
        }
        uniform = common;
        return total;
    }

    private long prepareMixed(ParallelJob job, PoolRows rows, int firstRow, int liveCount,
                              int common, int firstEstimate) {
        int rowLimit = rows.limit;
        int[] nextFree = rows.nextFree;
        if (estimates.length < rowLimit) {
            int required = liveCount;
            for (int row = firstRow; row < rowLimit; row++) {
                if (nextFree[row] == 0) {
                    required++;
                }
            }
            if (estimates.length < required) {
                estimates = new int[required];
            }
        }
        Arrays.fill(estimates, 0, liveCount, common);
        estimates[liveCount++] = firstEstimate;
        long total = firstEstimate;
        int[] values = estimates;
        for (int row = firstRow + 1; row < rowLimit; row++) {
            if (nextFree[row] != 0) {
                continue;
            }

            int estimate = estimate(job, row);
            values[liveCount++] = estimate;
            total += estimate;
        }
        return total;
    }

    private static int estimate(ParallelJob job, int row) {
        int estimate = job.estimateWorkload(row);
        if (estimate < 1) {
            throw new IllegalStateException("The estimated workload at index=" + row
                + " must be greater than or equal to 1. (Parallel job class: " + job.getClass().getName() + ").");
        }

        return estimate;
    }
}
