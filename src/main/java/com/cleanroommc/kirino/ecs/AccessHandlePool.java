package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.utils.ReflectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Prepares constructor and field handles once per Java layout.
 *
 * <p>Bindings are specific to runtime registration. Fields are resolved from the
 * concrete class toward its superclasses, and private access uses the field's declaring
 * class. Typed primitive handles let storage code move values without boxing.</p>
 */
class AccessHandlePool {
    private final Map<Class<?>, Access> membersMap = new Reference2ObjectOpenHashMap<>();

    public void register(Class<?> clazz, MemberLayout memberLayout) {
        Access access = prepare(clazz, memberLayout);
        membersMap.put(clazz, access);
    }

    /**
     * Resolves the no-argument constructor and mutable instance fields for one layout.
     *
     * @param clazz        Concrete schema class.
     * @param memberLayout Field names in layout order.
     * @return Prepared access handles for this class and field order.
     * @throws IllegalArgumentException If the constructor or a requested mutable field cannot be bound.
     */
    public static Access prepare(Class<?> clazz, MemberLayout memberLayout) {
        Preconditions.checkNotNull(clazz);
        Preconditions.checkNotNull(memberLayout);
        Preconditions.checkArgument(!Modifier.isAbstract(clazz.getModifiers()) && !clazz.isInterface(),
            "Class %s must be concrete.", clazz);

        try {
            MethodHandle constructor = ReflectionUtils.getConstructor(clazz);
            Preconditions.checkArgument(constructor != null, "Class %s requires an accessible no-argument constructor.", clazz);

            String[] fieldNames = memberLayout.fieldNames.elements();
            MethodHandle[] getters = new MethodHandle[fieldNames.length];
            MethodHandle[] primitiveGetters = new MethodHandle[fieldNames.length];
            MethodHandle[] primitiveSetters = new MethodHandle[fieldNames.length];
            MethodHandle[] setters = new MethodHandle[fieldNames.length];
            Class<?>[] types = new Class<?>[fieldNames.length];
            ImmutableMap.Builder<String, Integer> indexes = ImmutableMap.builder();
            for (int i = 0; i < fieldNames.length; i++) {
                String name = fieldNames[i];
                Field javaField = findField(clazz, name);
                VarHandle field = ReflectionUtils.getField(javaField.getDeclaringClass(), name, javaField.getType());
                Preconditions.checkArgument(field != null, "Cannot access field %s.%s.", clazz.getName(), name);
                Preconditions.checkArgument(field.coordinateTypes().size() == 1 && field.isAccessModeSupported(VarHandle.AccessMode.SET),
                    "Field %s.%s must be a mutable instance field.", clazz.getName(), name);

                getters[i] = field.toMethodHandle(VarHandle.AccessMode.GET).asType(MethodType.methodType(Object.class, Object.class));
                setters[i] = field.toMethodHandle(VarHandle.AccessMode.SET).asType(MethodType.methodType(void.class, Object.class, Object.class));
                Class<?> primitive = MethodType.methodType(field.varType()).unwrap().returnType();
                primitiveGetters[i] = field.toMethodHandle(VarHandle.AccessMode.GET).asType(MethodType.methodType(primitive, Object.class));
                primitiveSetters[i] = field.toMethodHandle(VarHandle.AccessMode.SET).asType(MethodType.methodType(void.class, Object.class, primitive));
                types[i] = field.varType();
                indexes.put(name, i);
            }
            return new Access(clazz, constructor.asType(MethodType.methodType(Object.class)), getters,
                setters, types, indexes.buildOrThrow(), primitiveGetters, primitiveSetters);
        } catch (NoSuchFieldException | IllegalStateException e) {
            throw new IllegalArgumentException("Cannot prepare fields of " + clazz.getName() + ".", e);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            Field field = ReflectionUtils.findDeclaredField(current, name);
            if (field != null) {
                return field;
            }
        }

        throw new NoSuchFieldException("Cannot find field " + clazz.getName() + "." + name + ".");
    }

    public boolean classRegistered(Class<?> clazz) {
        return membersMap.containsKey(Preconditions.checkNotNull(clazz));
    }

    public Object newClass(Class<?> clazz) {
        Access access = membersMap.get(Preconditions.checkNotNull(clazz));
        return access == null ? null : access.newInstance();
    }

    public void setFieldValue(Class<?> clazz, Object target, String fieldName, Object value) {
        requireAccess(clazz).set(Preconditions.checkNotNull(target), Preconditions.checkNotNull(fieldName), value);
    }

    public Object getFieldValue(Class<?> clazz, Object target, String fieldName) {
        return requireAccess(clazz).get(Preconditions.checkNotNull(target), Preconditions.checkNotNull(fieldName));
    }

    private Access requireAccess(Class<?> clazz) {
        Access access = membersMap.get(Preconditions.checkNotNull(clazz));
        Preconditions.checkArgument(access != null, "Class %s isn't registered.", clazz);

        return access;
    }

    public static final class Access {
        private final MethodHandle[] primitiveGetters;
        private final MethodHandle[] primitiveSetters;
        private final Class<?> javaClass;
        private final MethodHandle constructor;
        private final MethodHandle[] getters;
        private final MethodHandle[] setters;
        private final Class<?>[] types;
        private final ImmutableMap<String, Integer> indexes;

        private Access(Class<?> javaClass, MethodHandle constructor, MethodHandle[] getters,
                       MethodHandle[] setters, Class<?>[] types, ImmutableMap<String, Integer> indexes,
                       MethodHandle[] primitiveGetters, MethodHandle[] primitiveSetters) {
            this.primitiveGetters = primitiveGetters;
            this.primitiveSetters = primitiveSetters;
            this.javaClass = javaClass;
            this.constructor = constructor;
            this.getters = getters;
            this.setters = setters;
            this.types = types;
            this.indexes = indexes;
        }

        public Class<?> fieldType(int index) {
            return types[index];
        }

        public Object newInstance() {
            try {
                return (Object) constructor.invokeExact();
            } catch (Throwable e) {
                throw failure("construct", e);
            }
        }

        public Object get(Object target, String name) {
            return get(target, index(name));
        }

        public void set(Object target, String name, Object value) {
            set(target, index(name), value);
        }

        public Object get(Object target, int index) {
            try {
                return (Object) getters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void set(Object target, int index, Object value) {
            try {
                setters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public byte getByte(Object target, int index) {
            try {
                return (byte) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setByte(Object target, int index, byte value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public short getShort(Object target, int index) {
            try {
                return (short) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setShort(Object target, int index, short value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public int getInt(Object target, int index) {
            try {
                return (int) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setInt(Object target, int index, int value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public long getLong(Object target, int index) {
            try {
                return (long) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setLong(Object target, int index, long value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public float getFloat(Object target, int index) {
            try {
                return (float) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setFloat(Object target, int index, float value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public double getDouble(Object target, int index) {
            try {
                return (double) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setDouble(Object target, int index, double value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        public boolean getBool(Object target, int index) {
            try {
                return (boolean) primitiveGetters[index].invokeExact(target);
            } catch (Throwable e) {
                throw failure("read field " + index, e);
            }
        }

        public void setBool(Object target, int index, boolean value) {
            try {
                primitiveSetters[index].invokeExact(target, value);
            } catch (Throwable e) {
                throw failure("write field " + index, e);
            }
        }

        private int index(String name) {
            Integer index = indexes.get(name);
            Preconditions.checkArgument(index != null, "Unknown field %s in %s.", name, javaClass);

            return index;
        }

        private RuntimeException failure(String operation, Throwable cause) {
            if (cause instanceof Error error) {
                throw error;
            }

            return new IllegalStateException("Cannot " + operation + " in " + javaClass.getName() + ".", cause);
        }
    }
}
