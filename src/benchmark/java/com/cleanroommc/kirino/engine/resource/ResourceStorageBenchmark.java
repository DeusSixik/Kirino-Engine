package com.cleanroommc.kirino.engine.resource;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ResourceStorageBenchmark {
    private ResourceStorage storage;
    private ResourceSlot<Object> slot;
    private Int2ObjectMap<?> values;

    @Setup
    public void setup() throws ReflectiveOperationException {
        var constructor = ResourceStorage.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        storage = constructor.newInstance();
        slot = new ResourceSlot<>(5, Object.class, "meshlet registry");
        storage.put(slot, new Object());

        var field = ResourceStorage.class.getDeclaredField("storage");
        field.setAccessible(true);
        values = (Int2ObjectMap<?>) field.get(storage);
    }

    @Benchmark
    public Object eagerMessage() {
        Preconditions.checkNotNull(slot);
        Object result = values.get(slot.id());
        Preconditions.checkNotNull(result, "Can't resolve the resource \"%s\".", slot.toString());
        return result;
    }

    @Benchmark
    public Object guavaLazyMessage() {
        return storage.get(slot);
    }

    @Benchmark
    public Object manualNullCheck() {
        Preconditions.checkNotNull(slot);
        return getResource(slot);
    }

    @Benchmark
    public Object guavaSlotCheck() {
        ResourceSlot<Object> key = slot;
        Preconditions.checkNotNull(key);
        return getResource(key);
    }

    @Benchmark
    public Object manualSlotCheck() {
        ResourceSlot<Object> key = slot;
        if (key == null) {
            throw new NullPointerException();
        }
        return getResource(key);
    }

    private Object getResource(ResourceSlot<Object> key) {
        Object result = values.get(key.id());
        if (result == null) {
            throw new NullPointerException("Can't resolve the resource \"" + key + "\".");
        }
        return result;
    }
}
