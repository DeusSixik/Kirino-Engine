package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.storage.PrimitiveArray;
import com.cleanroommc.kirino.ecs.api.storage.PrimitiveArrayType;
import com.google.common.base.Preconditions;

/**
 * Owns a component layout's live columns within one pool.
 */
final class ColumnBinding implements PrimitiveValues {
    final SchemaLayout layout;
    final ObjectCodec codec;
    /**
     * Column wrappers in layout ordinal order. Their backing arrays may change on resize.
     */
    final HeapPrimitiveArray[] columns;
    final int componentIndex;

    ColumnBinding(SchemaLayout layout, ObjectCodec codec, int length, int componentIndex) {
        this.layout = layout;
        this.codec = codec;
        this.componentIndex = componentIndex;
        this.columns = new HeapPrimitiveArray[layout.unitCount()];

        SchemaLayout.Column[] elements = layout.columns().elements();
        for (int i = 0; i < columns.length; i++) {
            PrimitiveArrayType type = PrimitiveArrayType.valueOf(elements[i].type().name());
            columns[i] = new HeapPrimitiveArray(type, length);
        }
    }

    /**
     * Resolves a column descriptor against these live columns.
     *
     * @param column Descriptor owned by this binding's exact layout.
     * @return The live wrapper for the ordinal.
     * @throws IllegalArgumentException If the descriptor does not belong to the layout.
     */
    PrimitiveArray column(SchemaLayout.Column column) {
        Preconditions.checkNotNull(column);
        if (column.ordinal() >= columns.length || layout.columns().elements()[column.ordinal()] != column) {
            throw new IllegalArgumentException("Column doesn't belong to " + layout.name() + ".");
        }

        return columns[column.ordinal()];
    }

    public byte getByte(int column, int row) {
        return columns[column].getByte(row);
    }

    public void setByte(int column, int row, byte value) {
        columns[column].setByte(row, value);
    }

    public short getShort(int column, int row) {
        return columns[column].getShort(row);
    }

    public void setShort(int column, int row, short value) {
        columns[column].setShort(row, value);
    }

    public int getInt(int column, int row) {
        return columns[column].getInt(row);
    }

    public void setInt(int column, int row, int value) {
        columns[column].setInt(row, value);
    }

    public long getLong(int column, int row) {
        return columns[column].getLong(row);
    }

    public void setLong(int column, int row, long value) {
        columns[column].setLong(row, value);
    }

    public float getFloat(int column, int row) {
        return columns[column].getFloat(row);
    }

    public void setFloat(int column, int row, float value) {
        columns[column].setFloat(row, value);
    }

    public double getDouble(int column, int row) {
        return columns[column].getDouble(row);
    }

    public void setDouble(int column, int row, double value) {
        columns[column].setDouble(row, value);
    }

    public boolean getBool(int column, int row) {
        return columns[column].getBool(row);
    }

    public void setBool(int column, int row, boolean value) {
        columns[column].setBool(row, value);
    }
}
