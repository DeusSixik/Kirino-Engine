package com.cleanroommc.kirino.ecs;

/**
 * Tracks occupied rows and links reusable holes inside the active row range.
 *
 * <p>The pool holds structural access while mutating this descriptor. Within the range
 * from zero to {@link #limit} exclusive, a zero next link marks an occupied row. A hole
 * stores {@code nextRow + 2}, where a missing next row is encoded as one. Its previous
 * link stores {@code previousRow + 2} when present. A previous link below two marks
 * the head. Removing the last live row trims trailing holes from the active range.</p>
 */
final class PoolRows {
    /**
     * Upper exclusive bound of the active row range. This includes holes.
     */
    int limit;
    /**
     * Next hole links and occupied-row markers.
     */
    int[] nextFree;
    /**
     * Previous hole links used to unlink a hole without scanning.
     */
    int[] previousFree;
    /**
     * First reusable hole, or -1 when the active range has none.
     */
    private int freeHead = -1;

    PoolRows(int capacity) {
        nextFree = new int[capacity];
        previousFree = new int[capacity];
    }

    /**
     * Selects the next insertion row without publishing occupancy.
     *
     * @return The first reusable hole or the current row limit.
     */
    int nextRow() {
        return freeHead < 0 ? limit : freeHead;
    }

    boolean isOccupied(int row) {
        return row >= 0 && row < limit && nextFree[row] == 0;
    }

    /**
     * Publishes an insertion selected by {@link #nextRow()}.
     *
     * @param row Hole to unlink or the row at the current limit.
     */
    void occupy(int row) {
        if (row == limit) {
            limit++;
        } else {
            unlink(row);
        }
    }

    /**
     * Reclaims an occupied row and collapses any trailing holes.
     *
     * @param row Occupied row in the active range.
     */
    void release(int row) {
        if (row == limit - 1) {
            limit--;
            while (limit > 0 && nextFree[limit - 1] != 0) {
                unlink(limit - 1);
                limit--;
            }
        } else {
            nextFree[row] = freeHead + 2;
            previousFree[row] = 0;
            if (freeHead >= 0) {
                previousFree[freeHead] = row + 1;
            }
            freeHead = row;
        }
    }

    private void unlink(int row) {
        int previous = previousFree[row] - 1;
        int next = nextFree[row] - 2;
        if (previous < 0) {
            freeHead = next;
        } else {
            nextFree[previous] = next + 2;
        }
        if (next >= 0) {
            previousFree[next] = previous + 1;
        }
        nextFree[row] = 0;
        previousFree[row] = 0;
    }
}
