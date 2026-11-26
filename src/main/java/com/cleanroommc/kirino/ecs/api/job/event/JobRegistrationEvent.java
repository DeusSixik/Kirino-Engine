package com.cleanroommc.kirino.ecs.api.job.event;

import com.cleanroommc.kirino.ecs.api.job.ParallelJob;
import com.google.common.base.Preconditions;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects parallel job classes during runtime bootstrap after component schemas are prepared.
 */
public class JobRegistrationEvent extends Event {

    /**
     * Job classes validated by the runtime after event dispatch.
     */
    private final List<Class<? extends ParallelJob>> parallelJobClasses = new ArrayList<>();

    /**
     * Submits a job class for validation during bootstrap.
     *
     * @param parallelJobClass Concrete job class with a public no-argument constructor.
     * @throws NullPointerException If the class is null.
     */
    public void register(@NonNull Class<? extends ParallelJob> parallelJobClass) {
        Preconditions.checkNotNull(parallelJobClass);

        parallelJobClasses.add(parallelJobClass);
    }
}
