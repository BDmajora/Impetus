package com.bdmajora.impetus.engine.impl.util.collections.quadtree;

// Integer rectangle with an inclusive origin and an exclusive far edge.
public class Rect2i {
    // Left edge.
    protected final int x;
    // Top edge.
    protected final int y;
    // Width of the rectangle.
    protected final int width;
    // Height of the rectangle.
    protected final int height;

    // Creates a rectangle.
    public Rect2i(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // Creates a copy of a rectangle and rejects null.
    public Rect2i(Rect2i other) {
        this(other.x(), other.y(), other.width(), other.height());
    }

    // Returns whether the point lies inside the rectangle.
    public boolean contains(int x, int y) {
        return (long) x - this.x < this.width && (long) y - this.y < this.height && x >= this.x && y >= this.y;
    }

    // Returns whether the other rectangle lies entirely inside this one.
    public boolean contains(Rect2i other) {
        return other.width >= 0 && other.height >= 0
                && other.x >= this.x && other.y >= this.y
                && (long) other.x + other.width <= (long) this.x + this.width
                && (long) other.y + other.height <= (long) this.y + this.height;
    }

    // Returns the left edge.
    public int x() {
        return x;
    }

    // Returns the top edge.
    public int y() {
        return y;
    }

    // Returns the width.
    public int width() {
        return width;
    }

    // Returns the height.
    public int height() {
        return height;
    }

    // Compares the class and all four fields.
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Rect2i) obj;
        return this.x == that.x &&
                this.y == that.y &&
                this.width == that.width &&
                this.height == that.height;
    }

    // Combines all four fields.
    @Override
    public int hashCode() {
        int result = 31 + x;
        result = 31 * result + y;
        result = 31 * result + width;
        return 31 * result + height;
    }

    // Returns the fields as text.
    @Override
    public String toString() {
        return "Rect2i[" +
                "x=" + x + ", " +
                "y=" + y + ", " +
                "width=" + width + ", " +
                "height=" + height + ']';
    }

}
