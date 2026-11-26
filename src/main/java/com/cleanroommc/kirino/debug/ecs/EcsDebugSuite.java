package com.cleanroommc.kirino.debug.ecs;

import com.cleanroommc.kirino.ecs.api.CleanECSRuntime;
import com.cleanroommc.kirino.ecs.api.CleanEntityHandle;
import com.cleanroommc.kirino.ecs.api.EntityCommands;
import com.cleanroommc.kirino.ecs.api.EntityManager;
import com.cleanroommc.kirino.ecs.api.EntityQuery;
import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.cleanroommc.kirino.ecs.api.component.scan.CleanComponentSignature;
import com.cleanroommc.kirino.ecs.api.job.JobDataQuery;
import com.cleanroommc.kirino.ecs.api.job.JobExternalDataQuery;
import com.cleanroommc.kirino.ecs.api.job.JobScheduler.ExecutionHandle;
import com.cleanroommc.kirino.ecs.api.job.ParallelJob;
import com.cleanroommc.kirino.ecs.api.storage.PrimitiveArray;
import com.sun.management.ThreadMXBean;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

public final class EcsDebugSuite {
    enum Scenario {
        BULK, FRAGMENTED, MIGRATION, CONCURRENT, LIFECYCLE
    }

    record Result(Scenario scenario, long elapsedNanos, long allocatedBytes, int flushCount,
                  long flushNanos, long maxFlushNanos, boolean heldJobChecked) {
    }

    private static final String VERIFICATION = "kirino:debug-ecs/verification";
    private static final String[] VALUE_PATH = {"value"};
    private static final int ABSENT = -1;

    private EcsDebugSuite() {
    }

    static void run(CleanECSRuntime template, List<Scenario> scenarios, int rows, int workerCount,
                    Consumer<Result> report) throws Exception {
        long[] threadIds = new long[workerCount + 1];
        threadIds[0] = Thread.currentThread().threadId();
        int[] nextWorker = {1};
        try (ThreadPoolExecutor workers = new ThreadPoolExecutor(workerCount, workerCount, 0L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), task -> {
                int ordinal = nextWorker[0]++;
                Thread thread = new Thread(task, "Kirino ECS debug worker " + ordinal);
                thread.setDaemon(true);
                threadIds[ordinal] = thread.threadId();
                return thread;
            })) {
            workers.prestartAllCoreThreads();
            for (Scenario scenario : scenarios) {
                FlushMetrics flushes = new FlushMetrics();
                long before = allocatedBytes(threadIds);
                long start = System.nanoTime();
                boolean heldJobChecked = false;
                try (CleanECSRuntime runtime = template.newSession()) {
                    switch (scenario) {
                        case BULK -> bulk(runtime, rows, workers, flushes);
                        case FRAGMENTED -> fragmented(runtime, rows, workers, flushes);
                        case MIGRATION -> migration(runtime, rows, workers, flushes);
                        case CONCURRENT -> concurrent(runtime, rows, workerCount, workers, flushes);
                        case LIFECYCLE -> heldJobChecked = lifecycle(runtime, flushes);
                    }
                } catch (Exception failure) {
                    throw new IllegalStateException(scenario + " failed", failure);
                }
                long elapsed = System.nanoTime() - start;
                long after = allocatedBytes(threadIds);
                report.accept(new Result(scenario, elapsed, before < 0 || after < before ? -1 : after - before,
                    flushes.count, flushes.total, flushes.max, heldJobChecked));
            }
        }
    }

    private static void bulk(CleanECSRuntime runtime, int rows, Executor workers, FlushMetrics flushes) {
        EntityManager entities = runtime.entityManager;
        CleanEntityHandle[] handles = new CleanEntityHandle[rows];
        int[] callbacks = new int[2];
        for (int i = 0; i < rows; i++) {
            int id = i;
            handles[i] = entities.createEntity(context -> {
                check(context.getInt(Row.class, VALUE_PATH) == id + 1, "Destroy callback read the wrong value for row ", id);
                callbacks[1]++;
            }, context -> {
                check(context.getComponent(Row.class).value == id, "Create callback read the wrong value for row ", id);
                callbacks[0]++;
            }, row(i, i));
        }
        flushes.flush(entities);
        verify(runtime, expected(rows), true, workers);
        destroy(handles, flushes, entities);
        check(callbacks[0] == rows && callbacks[1] == rows, "Bulk callbacks did not run exactly once per entity");
        verifyEmpty(runtime, rows, workers);
    }

    private static void fragmented(CleanECSRuntime runtime, int rows, Executor workers, FlushMetrics flushes) {
        EntityManager entities = runtime.entityManager;
        for (int holes : new int[]{5, 9}) {
            CleanEntityHandle[] handles = populate(entities, rows);
            flushes.flush(entities);
            int[] expected = expected(rows);
            for (int i = 0; i < rows; i++) {
                if (i % 10 < holes) {
                    check(handles[i].tryDestroy(), "Could not remove row ", i);
                    expected[i] = ABSENT;
                }
            }
            flushes.flush(entities);
            verify(runtime, expected, true, workers);
            for (int i = 0; i < rows; i++) {
                if (expected[i] == ABSENT) {
                    CleanEntityHandle old = handles[i];
                    expected[i] = rows + i;
                    handles[i] = entities.createEntity(row(i, expected[i]));
                    check(!old.valid() && !old.tryDestroy(), "Deleted handle became usable again at row ", i);
                } else {
                    expected[i]++;
                }
            }
            flushes.flush(entities);
            verify(runtime, expected, false, workers);
            destroy(handles, flushes, entities);
            verifyEmpty(runtime, rows, workers);
        }
    }

    private static void migration(CleanECSRuntime runtime, int rows, Executor workers, FlushMetrics flushes) {
        EntityManager entities = runtime.entityManager;
        WidePayload[] payloads = {widePayload(3), widePayload(11)};
        CleanEntityHandle[] handles = new CleanEntityHandle[rows];
        for (int i = 0; i < rows; i++) {
            handles[i] = entities.createEntity(row(i, i), payloads[i % payloads.length]);
        }
        flushes.flush(entities);
        for (int i = 0; i < rows; i++) {
            Marker marker = new Marker();
            marker.tag = -i;
            check(handles[i].tryAddComponent(marker), "Could not add marker to row ", i);
        }
        flushes.flush(entities);
        int[] expected = expected(rows);
        verify(runtime, expected, true, workers);
        verifyWideRows(handles, payloads);
        for (int i = 0; i < rows; i++) {
            check(handles[i].readComponent(Marker.class).orElseThrow().tag == -i, "Wrong marker at row ", i);
            check(handles[i].tryRemoveComponent(Marker.class), "Could not remove marker from row ", i);
            expected[i]++;
        }
        flushes.flush(entities);
        for (CleanEntityHandle handle : handles) {
            check(!handle.contains(Marker.class), "Removed marker is still visible");
        }
        verify(runtime, expected, false, workers);
        verifyWideRows(handles, payloads);
        destroy(handles, flushes, entities);
        verifyEmpty(runtime, rows, workers);
    }

    private static void concurrent(CleanECSRuntime runtime, int rows, int workerCount, ExecutorService workers,
                                   FlushMetrics flushes) throws Exception {
        EntityManager entities = runtime.entityManager;
        CleanEntityHandle[] handles = new CleanEntityHandle[rows];
        int producers = Math.min(rows, workerCount);
        CountDownLatch firstCommands = new CountDownLatch(producers);
        CountDownLatch resume = new CountDownLatch(1);
        List<CompletableFuture<Void>> tasks = new ArrayList<>(producers);
        try {
            for (int p = 0; p < producers; p++) {
                int first = p;
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        handles[first] = entities.createEntity(row(first, first));
                    } finally {
                        firstCommands.countDown();
                    }
                    await(resume);
                    for (int i = first; i < rows; i += producers) {
                        CleanEntityHandle handle = i == first ? handles[i] : entities.createEntity(row(i, i));
                        handles[i] = handle;
                        check(handle.trySetComponent(row(i, rows + i)), "Concurrent SET failed at row ", i);
                        check(handle.tryAddComponent(new Marker()), "Concurrent ADD failed at row ", i);
                        check(handle.tryRemoveComponent(Marker.class), "Concurrent REMOVE failed at row ", i);
                        if (i % 2 == 0) {
                            check(handle.tryDestroy(), "Concurrent DESTROY failed at row ", i);
                        }
                    }
                }, workers));
            }
            await(firstCommands);
            flushes.flush(entities);
            resume.countDown();
            var done = CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
            while (!done.isDone()) {
                flushes.flush(entities);
                try {
                    done.get(2, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                }
            }
            done.join();
        } finally {
            resume.countDown();
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).handle((unused, failure) -> null).join();
        }
        flushes.flush(entities);
        int[] expected = new int[rows];
        for (int i = 0; i < rows; i++) {
            boolean survives = i % 2 != 0;
            expected[i] = survives ? rows + i : ABSENT;
            check(handles[i].valid() == survives && handles[i].isAlive() == survives,
                "Concurrent commands left the wrong entity state at row ", i);
            check(!survives || !handles[i].contains(Marker.class), "Concurrent REMOVE left a marker at row ", i);
        }
        verify(runtime, expected, false, workers);
    }

    private static boolean lifecycle(CleanECSRuntime runtime, FlushMetrics flushes) {
        EntityManager entities = runtime.entityManager;
        int rows = 8;
        int[] destroyed = {0};
        CleanEntityHandle[] old = new CleanEntityHandle[rows];
        for (int i = 0; i < rows; i++) {
            int id = i;
            old[i] = entities.createEntity(context -> {
                check(context.getInt(Row.class, VALUE_PATH) == id + 1, "Reset did not see the job result for row ", id);
                destroyed[0]++;
            }, null, row(i, i));
        }
        flushes.flush(entities);
        var pending = entities.createEntity(context -> {
            throw new IllegalStateException("An uncommitted entity reached its destroy callback");
        }, context -> {
            throw new IllegalStateException("Reset committed a pending entity");
        }, row(rows, rows));

        Verification verification = new Verification(expected(rows), true, true);
        List<Runnable> held = new ArrayList<>();
        boolean async;
        CompletableFuture<Void> drained;
        try {
            var execution = launch(runtime, verification, held::add);
            async = execution.async();
            if (async) {
                expectRejection(entities::flush, "flush while a job is admitted");
            }
            drained = runtime.quiesce();
            if (async) {
                check(!drained.isDone() && runtime.state() == CleanECSRuntime.State.QUIESCING,
                    "Quiesce completed before admitted jobs finished");
            }
            expectRejection(() -> entities.createEntity(row(rows, rows)), "create after quiesce");
        } finally {
            held.forEach(Runnable::run);
        }
        drained.join();
        verification.checkVisits();
        check(verification.queued != null, "The admitted job could not submit its deferred command");
        expectRejection(() -> verification.commands.createEntity(row(rows, rows)), "use of an expired job command writer");
        check(runtime.state() == CleanECSRuntime.State.QUIESCENT, "Quiesce never completed");
        runtime.reset();
        check(runtime.state() == CleanECSRuntime.State.RUNNING, "Reset did not reopen the session");
        check(!pending.valid() && !verification.queued.valid() && destroyed[0] == rows,
            "Reset did not discard pending data or clean committed rows");
        for (CleanEntityHandle handle : old) {
            check(!handle.valid() && !handle.tryDestroy(), "A handle survived reset");
        }
        verifyEmpty(runtime, rows, Runnable::run);
        populate(entities, rows);
        flushes.flush(entities);
        verify(runtime, expected(rows), false, Runnable::run);
        runtime.close();
        check(runtime.state() == CleanECSRuntime.State.CLOSED, "Close did not close the session");
        expectRejection(entities::flush, "flush after close");
        return async;
    }

    private static CleanEntityHandle[] populate(EntityManager entities, int rows) {
        CleanEntityHandle[] handles = new CleanEntityHandle[rows];
        for (int i = 0; i < rows; i++) {
            handles[i] = entities.createEntity(row(i, i));
        }
        return handles;
    }

    private static Row row(int id, int value) {
        Row row = new Row();
        row.id = id;
        row.value = value;
        row.position = new Vector3f(id, -id, value);
        return row;
    }

    private static WidePayload widePayload(int value) {
        WidePayload payload = new WidePayload();
        payload.first = new Matrix4f().translation(value, value + 1, value + 2);
        payload.second = new Matrix4f().scaling(value, value + 1, value + 2);
        payload.third = new Matrix4f().translation(-value, value + 3, -value - 4);
        payload.fourth = new Matrix4f().scaling(-value, value + 5, -value - 6);
        return payload;
    }

    private static void verifyWideRows(CleanEntityHandle[] handles, WidePayload[] payloads) {
        for (int i : new int[]{0, 1, handles.length / 2, handles.length - 1}) {
            if (i >= handles.length) {
                continue;
            }
            WidePayload actual = handles[i].readComponent(WidePayload.class).orElseThrow();
            WidePayload expected = payloads[i % payloads.length];
            check(expected.first.equals(actual.first) && expected.second.equals(actual.second)
                && expected.third.equals(actual.third) && expected.fourth.equals(actual.fourth),
                "Migration changed the wide payload at row ", i);
        }
    }

    private static int[] expected(int rows) {
        int[] expected = new int[rows];
        Arrays.setAll(expected, i -> i);
        return expected;
    }

    private static void destroy(CleanEntityHandle[] handles, FlushMetrics flushes, EntityManager entities) {
        for (CleanEntityHandle handle : handles) {
            check(handle.tryDestroy(), "Could not destroy a live test entity");
        }
        flushes.flush(entities);
        for (CleanEntityHandle handle : handles) {
            check(!handle.valid() && !handle.isAlive(), "Destroyed entity is still live");
        }
    }

    private static void verifyEmpty(CleanECSRuntime runtime, int rows, Executor workers) {
        int[] expected = new int[rows];
        Arrays.fill(expected, ABSENT);
        verify(runtime, expected, false, workers);
    }

    private static void verify(CleanECSRuntime runtime, int[] expected, boolean update, Executor workers) {
        Verification verification = new Verification(expected, update, false);
        launch(runtime, verification, workers).future().join();
        verification.checkVisits();
    }

    private static ExecutionHandle launch(CleanECSRuntime runtime, Verification verification, Executor workers) {
        return runtime.jobScheduler.executeParallelJob(runtime.entityManager, VerifyRows.class,
            Map.of(VERIFICATION, verification), workers);
    }

    private static void await(CountDownLatch latch) {
        try {
            check(latch.await(30, TimeUnit.SECONDS), "Timed out coordinating command producers");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating command producers", failure);
        }
    }

    private static void expectRejection(Runnable action, String description) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Expected rejection of " + description);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void check(boolean condition, String message, int row) {
        if (!condition) {
            throw new IllegalStateException(message + row);
        }
    }

    private static long allocatedBytes(long[] threadIds) {
        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean) || !bean.isThreadAllocatedMemoryEnabled()) {
            return -1;
        }
        long total = 0;
        for (long bytes : bean.getThreadAllocatedBytes(threadIds)) {
            if (bytes < 0) {
                return -1;
            }
            total += bytes;
        }
        return total;
    }

    private static final class FlushMetrics {
        private int count;
        private long total;
        private long max;

        void flush(EntityManager entities) {
            long start = System.nanoTime();
            entities.flush();
            long elapsed = System.nanoTime() - start;
            count++;
            total += elapsed;
            max = Math.max(max, elapsed);
        }
    }

    public static final class Verification {
        private final int[] expected;
        private final AtomicIntegerArray visits;
        private final boolean update;
        private final boolean queueFromJob;
        private EntityCommands commands;
        private CleanEntityHandle queued;

        Verification(int[] expected, boolean update, boolean queueFromJob) {
            this.expected = expected;
            this.visits = new AtomicIntegerArray(expected.length);
            this.update = update;
            this.queueFromJob = queueFromJob;
        }

        void checkVisits() {
            for (int i = 0; i < expected.length; i++) {
                if (visits.get(i) != (expected[i] == ABSENT ? 0 : 1)) {
                    throw new IllegalStateException("Job did not visit row " + i);
                }
            }
        }
    }

    @CleanComponentSignature
    public static final class Row implements CleanComponent {
        public int id;
        public int value;
        public Vector3f position;
    }

    @CleanComponentSignature
    public static final class Marker implements CleanComponent {
        public int tag;
    }

    @CleanComponentSignature
    public static final class WidePayload implements CleanComponent {
        public Matrix4f first;
        public Matrix4f second;
        public Matrix4f third;
        public Matrix4f fourth;
    }

    public static final class VerifyRows implements ParallelJob {
        @JobDataQuery(componentClass = Row.class, fieldAccessChain = {"id"})
        private PrimitiveArray ids;
        @JobDataQuery(componentClass = Row.class, fieldAccessChain = {"value"})
        private PrimitiveArray values;
        @JobDataQuery(componentClass = Row.class, fieldAccessChain = {"position", "x"})
        private PrimitiveArray positions;
        @JobExternalDataQuery(VERIFICATION)
        private Verification verification;

        @Override
        public void execute(EntityCommands commands, int index, int entityID, int threadOrdinal) {
            int id = ids.getInt(index);
            if (id < 0 || id >= verification.expected.length || verification.expected[id] == ABSENT) {
                throw new IllegalStateException("Job visited an unexpected row " + id);
            }
            int value = values.getInt(index);
            if (value != verification.expected[id] || positions.getFloat(index) != id || !verification.visits.compareAndSet(id, 0, 1)) {
                throw new IllegalStateException("Job visited row " + id + " twice or read incorrect component data");
            }
            if (verification.update) {
                values.setInt(index, value + 1);
            }
            if (verification.queueFromJob && id == 0) {
                verification.commands = commands;
                verification.queued = commands.createEntity(null, context -> {
                    throw new IllegalStateException("Reset committed a command from a job");
                }, row(verification.expected.length, 0));
            }
        }

        @Override
        public void query(EntityQuery query) {
            query.with(Row.class);
        }

        @Override
        public int estimateWorkload(int index) {
            return Integer.MAX_VALUE;
        }
    }
}
