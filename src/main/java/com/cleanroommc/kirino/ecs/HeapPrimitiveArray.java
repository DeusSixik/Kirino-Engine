package com.cleanroommc.kirino.ecs;

import com.cleanroommc.kirino.ecs.api.storage.PrimitiveArray;
import com.cleanroommc.kirino.ecs.api.storage.PrimitiveArrayType;

import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * Adapts one primitive array to a column whose wrapper identity survives resize.
 */
final class HeapPrimitiveArray implements PrimitiveArray {
    private final PrimitiveArrayType type;
    private int length;
    private byte[] byteArray;
    private short[] shortArray;
    private int[] intArray;
    private long[] longArray;
    private float[] floatArray;
    private double[] doubleArray;
    private boolean[] booleanArray;

    HeapPrimitiveArray(byte[] array) {
        type = PrimitiveArrayType.BYTE;
        length = array.length;
        byteArray = array;
    }

    HeapPrimitiveArray(short[] array) {
        type = PrimitiveArrayType.SHORT;
        length = array.length;
        shortArray = array;
    }

    HeapPrimitiveArray(int[] array) {
        type = PrimitiveArrayType.INT;
        length = array.length;
        intArray = array;
    }

    HeapPrimitiveArray(long[] array) {
        type = PrimitiveArrayType.LONG;
        length = array.length;
        longArray = array;
    }

    HeapPrimitiveArray(float[] array) {
        type = PrimitiveArrayType.FLOAT;
        length = array.length;
        floatArray = array;
    }

    HeapPrimitiveArray(double[] array) {
        type = PrimitiveArrayType.DOUBLE;
        length = array.length;
        doubleArray = array;
    }

    HeapPrimitiveArray(boolean[] array) {
        type = PrimitiveArrayType.BOOL;
        length = array.length;
        booleanArray = array;
    }

    HeapPrimitiveArray(PrimitiveArrayType type, int length) {
        this.type = type;
        this.length = length;
        switch (type) {
            case BYTE -> byteArray = new byte[length];
            case SHORT -> shortArray = new short[length];
            case INT -> intArray = new int[length];
            case LONG -> longArray = new long[length];
            case FLOAT -> floatArray = new float[length];
            case DOUBLE -> doubleArray = new double[length];
            case BOOL -> booleanArray = new boolean[length];
        }
    }

    /**
     * Copies a primitive value directly between same-type columns.
     *
     * @param sourceRow Occupied source row within this column.
     * @param target    Destination column of the same type, chosen by the transfer plan.
     * @param targetRow Allocated destination row to fill before publication.
     */
    void copyTo(int sourceRow, HeapPrimitiveArray target, int targetRow) {
        switch (type) {
            case BYTE -> target.byteArray[targetRow] = byteArray[sourceRow];
            case SHORT -> target.shortArray[targetRow] = shortArray[sourceRow];
            case INT -> target.intArray[targetRow] = intArray[sourceRow];
            case LONG -> target.longArray[targetRow] = longArray[sourceRow];
            case FLOAT -> target.floatArray[targetRow] = floatArray[sourceRow];
            case DOUBLE -> target.doubleArray[targetRow] = doubleArray[sourceRow];
            case BOOL -> target.booleanArray[targetRow] = booleanArray[sourceRow];
        }
    }

    /**
     * Allocates a replacement array and copies the retained range.
     *
     * @param newLength Requested capacity in rows.
     * @return The uninstalled primitive array with the same element type.
     */
    Object resizedArray(int newLength) {
        return switch (type) {
            case BYTE -> Arrays.copyOf(byteArray, newLength);
            case SHORT -> Arrays.copyOf(shortArray, newLength);
            case INT -> Arrays.copyOf(intArray, newLength);
            case LONG -> Arrays.copyOf(longArray, newLength);
            case FLOAT -> Arrays.copyOf(floatArray, newLength);
            case DOUBLE -> Arrays.copyOf(doubleArray, newLength);
            case BOOL -> Arrays.copyOf(booleanArray, newLength);
        };
    }

    /**
     * Installs a previously prepared backing array under structural access.
     *
     * @param array     Replacement array of the column's primitive type.
     * @param newLength Length of the replacement array in rows.
     */
    void replaceArray(Object array, int newLength) {
        switch (type) {
            case BYTE -> byteArray = (byte[]) array;
            case SHORT -> shortArray = (short[]) array;
            case INT -> intArray = (int[]) array;
            case LONG -> longArray = (long[]) array;
            case FLOAT -> floatArray = (float[]) array;
            case DOUBLE -> doubleArray = (double[]) array;
            case BOOL -> booleanArray = (boolean[]) array;
        }
        length = newLength;
    }

    @Override
    public byte getByte(int index) {
        checkType(PrimitiveArrayType.BYTE);
        return byteArray[index];
    }

    @Override
    public void setByte(int index, byte value) {
        checkType(PrimitiveArrayType.BYTE);
        byteArray[index] = value;
    }

    @Override
    public short getShort(int index) {
        checkType(PrimitiveArrayType.SHORT);
        return shortArray[index];
    }

    @Override
    public void setShort(int index, short value) {
        checkType(PrimitiveArrayType.SHORT);
        shortArray[index] = value;
    }

    @Override
    public int getInt(int index) {
        checkType(PrimitiveArrayType.INT);
        return intArray[index];
    }

    @Override
    public void setInt(int index, int value) {
        checkType(PrimitiveArrayType.INT);
        intArray[index] = value;
    }

    @Override
    public long getLong(int index) {
        checkType(PrimitiveArrayType.LONG);
        return longArray[index];
    }

    @Override
    public void setLong(int index, long value) {
        checkType(PrimitiveArrayType.LONG);
        longArray[index] = value;
    }

    @Override
    public float getFloat(int index) {
        checkType(PrimitiveArrayType.FLOAT);
        return floatArray[index];
    }

    @Override
    public void setFloat(int index, float value) {
        checkType(PrimitiveArrayType.FLOAT);
        floatArray[index] = value;
    }

    @Override
    public double getDouble(int index) {
        checkType(PrimitiveArrayType.DOUBLE);
        return doubleArray[index];
    }

    @Override
    public void setDouble(int index, double value) {
        checkType(PrimitiveArrayType.DOUBLE);
        doubleArray[index] = value;
    }

    @Override
    public boolean getBool(int index) {
        checkType(PrimitiveArrayType.BOOL);
        return booleanArray[index];
    }

    @Override
    public void setBool(int index, boolean value) {
        checkType(PrimitiveArrayType.BOOL);
        booleanArray[index] = value;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public PrimitiveArrayType type() {
        return type;
    }

    private void checkType(PrimitiveArrayType type) {
        if(this.type != type) {
            throw new IllegalArgumentException("Wrong primitive column type.");
        }
    }
}
