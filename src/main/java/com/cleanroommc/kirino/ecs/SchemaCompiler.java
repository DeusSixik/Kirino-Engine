package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.schemata.collections.ObjectArrayView;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.*;
import java.util.function.Function;

/**
 * Compiles registered members to ordered columns and a flattened-path index.
 */
final class SchemaCompiler {
    private SchemaCompiler() {
    }

    /**
     * Assigns column ordinals in member order and expands nested layouts into that order.
     *
     * @param name         Diagnostic name of the registered schema.
     * @param javaClass    Java class represented by the layout.
     * @param definitions  Field definitions in the same order as the member names.
     * @param memberLayout Ordered Java field names.
     * @param structs      Resolver for previously prepared, acyclic struct definitions.
     * @return The prepared layout with position and path metadata.
     * @throws IllegalArgumentException If member metadata is inconsistent or a nested struct cannot be resolved.
     */
    public static SchemaLayout compile(String name, Class<?> javaClass, List<FieldDef> definitions,
                                       MemberLayout memberLayout, Function<String, SchemaLayout> structs) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(javaClass);
        Preconditions.checkNotNull(structs);
        Preconditions.checkArgument(definitions.size() == memberLayout.fieldNames.size(),
            "Field names and definitions have different lengths in %s.", name);

        List<SchemaLayout.Member> members = new ObjectArrayList<>();
        List<SchemaLayout.Column> columns = new ObjectArrayList<>();
        Map<String, MutableNode> paths = new Object2ObjectOpenHashMap<>();
        int[] counts = new int[FlattenedScalarType.values().length];
        String[] fieldNames = memberLayout.fieldNames.elements();
        for (int i = 0; i < definitions.size(); i++) {
            String fieldName = fieldNames[i];
            Preconditions.checkArgument(paths.putIfAbsent(fieldName, new MutableNode()) == null,
                "Duplicate field %s in %s.", fieldName, name);

            FieldDef definition = definitions.get(i);
            SchemaLayout nested = definition.fieldKind == FieldKind.STRUCT ? structs.apply(definition.structTypeName) : null;
            int first = columns.size();
            if (nested != null) {
                for (SchemaLayout.Column column : nested.columns().elements()) {
                    String[] nestedPath = column.path().elements();
                    String[] path = new String[nestedPath.length + 1];
                    path[0] = fieldName;
                    System.arraycopy(nestedPath, 0, path, 1, nestedPath.length);
                    addColumn(ObjectArrayView.copyOf(path),
                        column.type(), columns, counts, paths);
                }
            } else if (definition.fieldKind == FieldKind.SCALAR) {
                ScalarType scalar = definition.scalarType;
                FlattenedScalarType[] types = scalar.flattenedTypes.elements();
                if (scalar.isSingular()) {
                    addColumn(ObjectArrayView.of(fieldName), types[0], columns, counts, paths);
                } else {
                    String[] fieldsByOrdinal = new String[types.length];
                    for (String field : scalar.fieldNames) {
                        fieldsByOrdinal[scalar.ordinalOffsetOfField(field)] = field;
                    }
                    for (int j = 0; j < fieldsByOrdinal.length; j++) {
                        addColumn(ObjectArrayView.of(fieldName, fieldsByOrdinal[j]), types[j], columns, counts, paths);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unresolved struct " + definition.structTypeName + " in " + name + ".");
            }
            members.add(new SchemaLayout.Member(fieldName, definition, first, columns.size() - first, nested));
        }
        return new SchemaLayout(name, javaClass, members, columns, freeze(paths));
    }

    private static void addColumn(ObjectArrayView<String> path, FlattenedScalarType type,
                                  List<SchemaLayout.Column> columns, int[] counts, Map<String, MutableNode> paths) {
        SchemaLayout.Column column = new SchemaLayout.Column(path, type, columns.size(), counts[type.ordinal()]++);
        columns.add(column);
        MutableNode node = null;
        Map<String, MutableNode> children = paths;
        for (String segment : path.elements()) {
            node = children.computeIfAbsent(segment, key -> new MutableNode());
            children = node.children;
        }
        node.column = column;
    }

    private static Map<String, SchemaLayout.PathNode> freeze(Map<String, MutableNode> nodes) {
        Map<String, SchemaLayout.PathNode> result = new Object2ObjectLinkedOpenHashMap<>();
        nodes.forEach((name, node) -> result.put(name,
            new SchemaLayout.PathNode(freeze(node.children), node.column))
        );
        return result;
    }

    private static final class MutableNode {
        final Map<String, MutableNode> children = new Object2ObjectLinkedOpenHashMap<>();
        SchemaLayout.Column column;
    }
}
