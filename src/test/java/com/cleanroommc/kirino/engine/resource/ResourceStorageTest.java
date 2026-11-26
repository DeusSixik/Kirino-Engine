package com.cleanroommc.kirino.engine.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceStorageTest {

    @Test
    void get_presentResource_returnsStoredInstance() throws ReflectiveOperationException {
        var storage = newStorage();
        var slot = new ResourceSlot<>(5, Object.class, "meshlet registry");
        var resource = new Object();
        storage.put(slot, resource);

        assertSame(resource, storage.get(slot));
    }

    @Test
    void get_missingResource_includesSlotDetailsInException() throws ReflectiveOperationException {
        var storage = newStorage();
        var slot = new ResourceSlot<>(5, Object.class, "meshlet registry");

        var failure = assertThrows(NullPointerException.class, () -> storage.get(slot));

        assertEquals("Can't resolve the resource \"" + slot + "\".", failure.getMessage());
    }

    @Test
    void sealOperations_missingResource_rejectWithSlotDetails() throws ReflectiveOperationException {
        var storage = newStorage();
        var slot = new ResourceSlot<>(5, Object.class, "meshlet registry");
        String message = "Resource \"" + slot + "\" isn't in the storage yet.";

        assertEquals(message, assertThrows(IllegalStateException.class, () -> storage.isResourceSealed(slot)).getMessage());
        assertEquals(message, assertThrows(IllegalStateException.class, () -> storage.sealResource(slot)).getMessage());
    }

    @Test
    void mutations_sealedResource_rejectWithSlotDetailsAndKeepResource() throws ReflectiveOperationException {
        var storage = newStorage();
        var slot = new ResourceSlot<>(5, Object.class, "meshlet registry");
        var resource = new Object();
        storage.put(slot, resource);
        assertFalse(storage.isResourceSealed(slot));
        storage.sealResource(slot);
        assertTrue(storage.isResourceSealed(slot));
        String message = "The resource \"" + slot + "\" is sealed. You are no longer allowed to make changes.";

        assertEquals(message, assertThrows(IllegalStateException.class, () -> storage.put(slot, new Object())).getMessage());
        assertEquals(message, assertThrows(IllegalStateException.class, () -> storage.remove(slot)).getMessage());
        assertSame(resource, storage.get(slot));
    }

    @Test
    void mutations_sealedStorage_reportsStorageBeforeCheckingSlot() throws ReflectiveOperationException {
        var storage = newStorage();
        var seal = ResourceStorage.class.getDeclaredMethod("seal");
        seal.setAccessible(true);
        seal.invoke(storage);
        String message = "The storage is sealed. You are no longer allowed to make changes.";

        assertEquals(message, assertThrows(IllegalStateException.class, () -> storage.put(null, new Object())).getMessage());
        assertEquals(message, assertThrows(IllegalStateException.class, () -> storage.remove(null)).getMessage());
    }

    private static ResourceStorage newStorage() throws ReflectiveOperationException {
        var constructor = ResourceStorage.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
