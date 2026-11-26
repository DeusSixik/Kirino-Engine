package com.cleanroommc.kirino.ecs.api.component.scan.event;

import com.google.common.base.Preconditions;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects packages to search for ECS struct declarations during runtime creation.
 */
public final class StructScanningEvent extends Event {

    /**
     * Package names consumed by the scanner after event dispatch.
     */
    private final List<String> scanPackageNames = new ArrayList<>();

    /**
     * Adds a package to this scan request during event dispatch.
     *
     * @param packageName Java package name to scan recursively.
     * @throws NullPointerException If the package name is null.
     */
    public void register(@NonNull String packageName) {
        Preconditions.checkNotNull(packageName);

        scanPackageNames.add(packageName);
    }
}
