package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.component.CleanComponent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Stores component definitions indexed by registered name and exact Java class.
 *
 * <p>Bootstrap owns registration. After locking, sessions can share schemas and codecs
 * for concurrent reads. Treat the published layouts and their arrays as read-only.</p>
 */
class ComponentRegistry {
    private final FieldRegistry fieldRegistry;

    public ComponentRegistry(FieldRegistry fieldRegistry) {
        this.fieldRegistry = fieldRegistry;
    }

    private final Map<Class<?>, Data> componentDataByClass = new Reference2ObjectOpenHashMap<>();
    private final Map<String, Data> componentData = new Object2ObjectOpenHashMap<>();

    private boolean lockRegistry = false;

    /**
     * Registers a component and compiles its flattened layout before publishing either index.
     *
     * @param name           Unique component registry name.
     * @param clazz          Exact concrete component class.
     * @param memberLayout   Java fields in schema order.
     * @param fieldTypeNames Registered field type name for each member in the same order.
     * @throws IllegalStateException    If registration is closed or the name is taken.
     * @throws IllegalArgumentException If the class is already registered or the schema is invalid.
     * @throws NullPointerException     If a required argument or field type name is null.
     */
    public synchronized void registerComponent(
        @NonNull String name,
        @NonNull Class<? extends CleanComponent> clazz,
        @NonNull MemberLayout memberLayout,
        @NonNull String @NonNull ... fieldTypeNames) {

        Preconditions.checkState(!lockRegistry, "The registry is already locked!");
        Preconditions.checkNotNull(name);
        Preconditions.checkState(!componentData.containsKey(name),
            "Component \"%s\" is already registered.", name);
        Preconditions.checkNotNull(clazz);
        Preconditions.checkNotNull(memberLayout);
        Preconditions.checkNotNull(fieldTypeNames);
        for (String fieldType : fieldTypeNames) {
            Preconditions.checkNotNull(fieldType);
        }

        Preconditions.checkArgument(!componentDataByClass.containsKey(clazz), "Component class %s is already registered.", clazz.getName());
        List<FieldDef> fields = new ArrayList<>();
        for (String fieldTypeName : fieldTypeNames) {
            FieldDef field = fieldRegistry.getFieldDef(fieldTypeName);
            Preconditions.checkArgument(field != null,
                "Field type %s doesn't exist.", fieldTypeName);

            fields.add(field);
        }

        ComponentDesc componentDesc = new ComponentDesc(name, fields, fieldTypeNames);
        Data data = new Data(name, clazz, componentDesc, memberLayout,
            SchemaCompiler.compile(name, clazz, fields, memberLayout, fieldRegistry.structRegistry::getLayout));
        componentDataByClass.put(clazz, data);
        componentData.put(name, data);
    }

    /**
     * Prepares codecs for every registered component and publishes them as one locked set.
     *
     * @throws IllegalStateException    If the registry is already locked.
     * @throws IllegalArgumentException If a layout cannot be bound to its Java class.
     */
    public synchronized void lock() {
        Preconditions.checkState(!lockRegistry, "The registry is already locked!");
        Map<Data, ObjectCodec> prepared = new IdentityHashMap<>();
        ObjectCodec.Factory factory = new ObjectCodec.Factory();
        for (Data data : componentData.values()) {
            prepared.put(data, factory.prepare(data.layout));
        }
        prepared.forEach((data, codec) -> data.codec = codec);
        lockRegistry = true;
    }

    //<editor-fold desc="getters & queries">
    @NonNull
    public ImmutableMap<String, Data> getComponentDataMap() {
        return ImmutableMap.copyOf(componentData);
    }

    public boolean componentExists(@NonNull Class<? extends CleanComponent> clazz) {
        Preconditions.checkNotNull(clazz);

        return componentDataByClass.containsKey(clazz);
    }

    public boolean componentExists(@NonNull String name) {
        Preconditions.checkNotNull(name);

        return componentData.containsKey(name);
    }

    @Nullable
    public MemberLayout getClassMemberLayout(@NonNull String name) {
        Preconditions.checkNotNull(name);

        Data data = componentData.get(name);
        if (data == null) {
            return null;
        }

        return data.memberLayout;
    }

    @Nullable
    public String getComponentName(@NonNull Class<? extends CleanComponent> clazz) {
        Preconditions.checkNotNull(clazz);

        Data data = componentDataByClass.get(clazz);
        if (data == null) {
            return null;
        }

        return data.name;
    }

    @Nullable
    public Class<? extends CleanComponent> getComponentClass(@NonNull String name) {
        Preconditions.checkNotNull(name);

        Data data = componentData.get(name);
        if (data == null) {
            return null;
        }

        return data.componentClass;
    }

    @Nullable
    public ComponentDesc getComponentDesc(@NonNull String name) {
        Preconditions.checkNotNull(name);

        Data data = componentData.get(name);
        if (data == null) {
            return null;
        }

        return data.componentDesc;
    }

    //</editor-fold>

    /**
     * @param name             Component name must be valid
     * @param fieldAccessChain The field access chain must be valid
     */
    @SuppressWarnings("DataFlowIssue")
    public int getFieldOrdinal(@NonNull String name, @NonNull String @NonNull ... fieldAccessChain) {
        return getLayout(name).resolve(fieldAccessChain).ordinal();
    }

    public SchemaLayout getLayout(String name) {
        Data data = componentData.get(Preconditions.checkNotNull(name));
        if (data == null) {
            throw new IllegalArgumentException("Component type " + name + " doesn't exist.");
        }

        return data.layout;
    }

    public SchemaLayout getLayout(Class<? extends CleanComponent> clazz) {
        Data data = componentDataByClass.get(Preconditions.checkNotNull(clazz));

        if (data == null) {
            throw new IllegalArgumentException("Component class " + clazz + " isn't registered.");
        }

        return data.layout;
    }

    public ObjectCodec getCodec(Class<? extends CleanComponent> component) {
        Data data = componentDataByClass.get(Preconditions.checkNotNull(component));
        if (data == null) {
            throw new IllegalArgumentException("Component class " + component + " isn't registered.");
        }

        return codec(data);
    }

    private ObjectCodec codec(Data data) {
        ObjectCodec codec = data.codec;
        return codec == null ? prepareCodec(data) : codec;
    }

    private synchronized ObjectCodec prepareCodec(Data data) {
        if (data.codec == null) {
            data.codec = new ObjectCodec(data.layout);
        }
        return data.codec;
    }

    // -----Component Construction-----

    /**
     * @param name Component name is not necessarily valid
     * @param args Arguments must match the flattened component one-by-one
     */
    @Nullable
    @SuppressWarnings("DataFlowIssue")
    public CleanComponent newComponent(@NonNull String name, @NonNull Object @NonNull ... args) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(args);

        for (Object arg : args) {
            Preconditions.checkNotNull(arg);
        }

        Data data = componentData.get(name);
        return data == null ? null : (CleanComponent) codec(data).create(args);
    }

    // -----Component Deconstruction-----

    @SuppressWarnings("DataFlowIssue")
    public @NonNull Object @NonNull [] flattenComponent(@NonNull CleanComponent component) {
        Class<?> componentClass = Preconditions.checkNotNull(component).getClass();
        Data data = componentDataByClass.get(componentClass);

        if(data == null) {
            throw new IllegalArgumentException("Component type " + componentClass + " isn't registered.");
        }

        return codec(data).flatten(component);
    }

    public static class Data {

        public final String name;
        public final Class<? extends CleanComponent> componentClass;
        public final ComponentDesc componentDesc;
        public final MemberLayout memberLayout;
        public final SchemaLayout layout;
        private volatile ObjectCodec codec;

        public Data(String name, Class<? extends CleanComponent> componentClass, ComponentDesc componentDesc, MemberLayout memberLayout, SchemaLayout layout) {
            this.name = name;
            this.componentClass = componentClass;
            this.componentDesc = componentDesc;
            this.memberLayout = memberLayout;
            this.layout = layout;
        }
    }
}
