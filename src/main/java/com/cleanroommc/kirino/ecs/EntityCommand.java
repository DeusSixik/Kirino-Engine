package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;
import com.cleanroommc.kirino.schemata.collections.ObjectArrayView;

/**
 * Carries one accepted structural command until the manager commits or discards it.
 *
 * @param index         Manager-local slot index.
 * @param generation    Slot generation captured at admission.
 * @param type          Operation to apply.
 * @param components    Shallow array snapshot for CREATE, otherwise null.
 * @param component     Borrowed value for ADD or SET, otherwise null.
 * @param componentType Affected type for component commands, otherwise null.
 * @param target        Target composition for CREATE, ADD, or REMOVE, otherwise null.
 */
record EntityCommand(int index, int generation, Type type, ObjectArrayView<CleanComponent> components,
                     CleanComponent component, Class<? extends CleanComponent> componentType,
                     ArchetypeKey target) {
    enum Type {
        CREATE, DESTROY, SET_COM, ADD_COM, REMOVE_COM
    }
}
