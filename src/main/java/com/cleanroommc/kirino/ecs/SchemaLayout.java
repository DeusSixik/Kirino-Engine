package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.schemata.collections.ObjectArrayView;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

/**
 * Describes ordered Java members and their flattened primitive columns.
 *
 * <p>Members own contiguous ranges of column ordinals. A path index resolves names to
 * column metadata. Layouts are shared after registration. Treat backing arrays and path
 * segments as read-only so the index and positional metadata stay consistent.</p>
 */
final class SchemaLayout {
    private final String name;
    private final Class<?> javaClass;
    /**
     * Top-level Java fields in schema order, including their flattened ranges.
     */
    private final ObjectArrayView<Member> members;
    /**
     * Primitive columns indexed by their global ordinal in this layout.
     */
    private final ObjectArrayView<Column> columns;
    /**
     * Path trie keyed by field names. Each resolved leaf refers to a column in the layout.
     */
    private final ImmutableMap<String, PathNode> paths;

    SchemaLayout(String name, Class<?> javaClass, List<Member> members,
                 List<Column> columns, Map<String, PathNode> paths) {
        this.name = name;
        this.javaClass = javaClass;
        this.members = ObjectArrayView.copyOf(members, Member[]::new);
        this.columns = ObjectArrayView.copyOf(columns, Column[]::new);
        this.paths = ImmutableMap.copyOf(paths);
    }

    public String name() {
        return name;
    }

    public Class<?> javaClass() {
        return javaClass;
    }

    /**
     * Returns the ordered member descriptors.
     *
     * @return Layout-owned metadata. Treat the backing array as read-only.
     */
    public ObjectArrayView<Member> members() {
        return members;
    }

    /**
     * Returns the flattened column descriptors.
     *
     * @return Layout-owned metadata in ordinal order. Treat the backing array as read-only.
     */
    public ObjectArrayView<Column> columns() {
        return columns;
    }

    /**
     * Counts the primitive values required for one instance.
     *
     * @return Number of primitive columns.
     */
    public int unitCount() {
        return columns.size();
    }

    /**
     * Resolves a field path to its primitive leaf.
     *
     * @param path Nonempty sequence of field names starting at the Java type.
     * @return The layout-owned column for that path.
     * @throws NullPointerException     If the path or a segment is null.
     * @throws IllegalArgumentException If the path is empty, unknown, or ends at a composite field.
     */
    public Column resolve(String... path) {
        Preconditions.checkNotNull(path);
        if (path.length == 0) {
            throw new IllegalArgumentException("Field path must not be empty for " + name + ".");
        }

        PathNode node = null;
        Map<String, PathNode> children = paths;
        for (String segment : path) {
            Preconditions.checkNotNull(segment);
        }
        for (String segment : path) {
            node = children.get(segment);
            if (node == null) {
                throw new IllegalArgumentException("Invalid field path " + String.join(".", path) + " in " + name + ".");
            }

            children = node.children;
        }
        if (node.column == null) {
            throw new IllegalArgumentException("Field path must reach a primitive field: " + String.join(".", path) + " in " + name + ".");
        }

        return node.column;
    }

    /**
     * Describes one Java member and the contiguous range it occupies in this layout.
     *
     * @param name         Java field name.
     * @param definition   Registered field definition.
     * @param firstOrdinal First column ordinal assigned to this field.
     * @param unitCount    Number of flattened columns in the range.
     * @param nested       Prepared layout for a struct, or null for a scalar.
     */
    public record Member(String name, FieldDef definition, int firstOrdinal, int unitCount, SchemaLayout nested) {
    }

    /**
     * Identifies the storage column for one primitive leaf.
     *
     * @param path      Field names from the root. Treat the backing array as read-only.
     * @param type      Exact primitive storage type.
     * @param ordinal   Zero-based position among all primitive columns.
     * @param typeIndex Zero-based position among columns of the same primitive type.
     */
    public record Column(ObjectArrayView<String> path, FlattenedScalarType type, int ordinal, int typeIndex) {
        public Column {
            Preconditions.checkNotNull(path);
            Preconditions.checkNotNull(type);
            if (path.isEmpty() || ordinal < 0 || typeIndex < 0) {
                throw new IllegalArgumentException("Invalid column metadata.");
            }
        }
    }

    static final class PathNode {
        final ImmutableMap<String, PathNode> children;
        final Column column;

        PathNode(Map<String, PathNode> children, Column column) {
            this.children = ImmutableMap.copyOf(children);
            this.column = column;
        }
    }
}
