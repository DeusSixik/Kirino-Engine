package com.cleanroommc.kirino.engine.render.usage.scene;

import com.cleanroommc.kirino.ecs.api.CleanECSRuntime;
import com.cleanroommc.kirino.ecs.api.EntityManager;
import com.cleanroommc.kirino.ecs.api.job.JobScheduler;
import com.cleanroommc.kirino.ecs.api.system.exegraph.SingleFlow;
import com.cleanroommc.kirino.ecs.apiconsumer.RuntimeApiTest;
import com.cleanroommc.kirino.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftSceneFlowTest {
    private static final MethodHandle FLUSH = ReflectionUtils.getMethod(MinecraftScene.class, "condFlushECS", void.class);

    @TempDir
    Path home;

    @ParameterizedTest
    @ValueSource(strings = {"chunkPrioritizationSystem", "chunkMeshletGenSystem", "meshletDestroySystem",
        "meshletDebugSystem", "meshletBufferWriteSystem"})
    void flowFailure_preventsFlushWorldChangesAndPublishingRenderOutput(String field) throws Throwable {
        try (CleanECSRuntime runtime = RuntimeApiTest.runtime(home)) {
            AtomicInteger flushes = new AtomicInteger();
            MinecraftScene scene = scene(flushes, runtime.jobScheduler);
            MethodHandle getter = ReflectionUtils.getFieldGetter(MinecraftScene.class, field, SingleFlow.class);
            SingleFlow<?> flow = (SingleFlow<?>) getter.invokeExact(scene);
            var completion = flow.executeAsync(Runnable::run);
            Throwable cause = assertThrows(CompletionException.class, completion::join).getCause();
            assertInstanceOf(IllegalArgumentException.class, cause);
            assertFalse(flow.isExecuting());
            assertSame(cause, assertThrows(RuntimeException.class, () -> {
                FLUSH.invokeExact(scene);
            }));
            assertSame(cause, assertThrows(RuntimeException.class, scene::update));
            assertSame(cause, assertThrows(RuntimeException.class, () -> scene.tryUpdateWorld(null)));
            assertSame(cause, assertThrows(RuntimeException.class, scene::isMeshletRenderReady));
            assertSame(cause, assertThrows(RuntimeException.class, scene::getMeshletRenderPayload));
            assertEquals(0, flushes.get());
        }
    }

    @Test
    void healthyScene_withoutWorldCanRemainIdleAndFlushNormally() throws Throwable {
        try (CleanECSRuntime runtime = RuntimeApiTest.runtime(home)) {
            AtomicInteger flushes = new AtomicInteger();
            MinecraftScene scene = scene(flushes, runtime.jobScheduler);
            scene.tryUpdateWorld(null);
            scene.update();
            assertFalse(scene.isMeshletRenderReady());
            assertNotNull(scene.getMeshletRenderPayload());
            FLUSH.invokeExact(scene);
            assertEquals(1, flushes.get());
        }
    }

    private static MinecraftScene scene(AtomicInteger flushes, JobScheduler scheduler) {
        EntityManager entities = (EntityManager) Proxy.newProxyInstance(MinecraftSceneFlowTest.class.getClassLoader(),
            new Class<?>[]{EntityManager.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("flush")) {
                    flushes.incrementAndGet();
                    return null;
                }
                throw new AssertionError("Unexpected manager operation: " + method.getName());
            });
        return new MinecraftScene(null, entities, scheduler, null, null, null, null, null, Runnable::run, Runnable::run);
    }
}
