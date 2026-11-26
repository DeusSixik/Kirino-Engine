package com.cleanroommc.kirino.engine.resource;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.NonNull;

public final class ResourceStorage {

    private final Int2ObjectMap<Object> storage = new Int2ObjectOpenHashMap<>();
    private final Int2BooleanMap resourceSealed = new Int2BooleanOpenHashMap();

    private ResourceStorage() {
    }

    private boolean sealed = false;

    public boolean isStorageSealed() {
        return sealed;
    }

    private void seal() {
        sealed = true;
    }

    public <T> boolean isResourceSealed(@NonNull ResourceSlot<T> slot) {
        Preconditions.checkNotNull(slot);
        if (!has(slot)) {
            throw new IllegalStateException("Resource \"" + slot + "\" isn't in the storage yet.");
        }

        return isResourceSealedInternal(slot);
    }

    private <T> boolean isResourceSealedInternal(@NonNull ResourceSlot<T> slot) {
        if (sealed) {
            return true;
        }

        return resourceSealed.get(slot.id());
    }

    public <T> void sealResource(@NonNull ResourceSlot<T> slot) {
        Preconditions.checkNotNull(slot);
        if (!has(slot)) {
            throw new IllegalStateException("Resource \"" + slot + "\" isn't in the storage yet.");
        }

        resourceSealed.put(slot.id(), true);
    }

    public <T> boolean has(@NonNull ResourceSlot<T> slot) {
        Preconditions.checkNotNull(slot);

        return storage.containsKey(slot.id());
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull ResourceSlot<T> slot) {
        Preconditions.checkNotNull(slot);

        T result = (T) storage.get(slot.id());
        if (result == null) {
            throw new NullPointerException("Can't resolve the resource \"" + slot + "\".");
        }
        return result;
    }

    public <T> void put(@NonNull ResourceSlot<T> slot, @NonNull T resource) {
        if (sealed) {
            throw new IllegalStateException("The storage is sealed. You are no longer allowed to make changes.");
        }
        Preconditions.checkNotNull(slot);
        Preconditions.checkNotNull(resource);
        if (isResourceSealedInternal(slot)) {
            throw new IllegalStateException("The resource \"" + slot + "\" is sealed. You are no longer allowed to make changes.");
        }

        storage.put(slot.id(), resource);
    }

    public void remove(@NonNull ResourceSlot<?> slot) {
        if (sealed) {
            throw new IllegalStateException("The storage is sealed. You are no longer allowed to make changes.");
        }
        Preconditions.checkNotNull(slot);
        if (isResourceSealedInternal(slot)) {
            throw new IllegalStateException("The resource \"" + slot + "\" is sealed. You are no longer allowed to make changes.");
        }

        storage.remove(slot.id());
    }
}
