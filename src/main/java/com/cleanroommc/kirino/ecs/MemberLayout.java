package com.cleanroommc.kirino.ecs;

import com.google.common.base.Preconditions;
import com.cleanroommc.kirino.schemata.collections.ObjectArrayView;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Snapshots Java field names in the order used by schema definitions and access handles.
 */
class MemberLayout {
    /**
     * Ordered names for this layout. Treat the backing array as read-only.
     */
    public final ObjectArrayView<String> fieldNames;

    /**
     * Copies the ordered member names into typed array storage.
     *
     * @param fieldNames Non-null names in field definition order.
     * @throws NullPointerException If the list or a name is null.
     */
    public MemberLayout(@NonNull List<@NonNull String> fieldNames) {
        Preconditions.checkNotNull(fieldNames);
        for (String fieldName : fieldNames) {
            Preconditions.checkNotNull(fieldName);
        }

        this.fieldNames = ObjectArrayView.copyOf(fieldNames, String[]::new);
    }

    /**
     * Copies a member name array in schema order.
     *
     * @param fieldNames Non-null names in field definition order.
     * @throws NullPointerException If the array or a name is null.
     */
    public MemberLayout(@NonNull String @NonNull ... fieldNames) {
        Preconditions.checkNotNull(fieldNames);
        for (String fieldName : fieldNames) {
            Preconditions.checkNotNull(fieldName);
        }

        this.fieldNames = ObjectArrayView.copyOf(fieldNames);
    }
}
