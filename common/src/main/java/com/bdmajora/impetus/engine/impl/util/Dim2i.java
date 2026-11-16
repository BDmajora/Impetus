package com.bdmajora.impetus.engine.impl.util;

// Rectangle of integers.
public record Dim2i(int x, int y, int width, int height) implements Point2i {
    // Returns the right edge.
    public int getLimitX() {
        return this.x + this.width;
    }

    // Returns the bottom edge.
    public int getLimitY() {
        return this.y + this.height;
    }

    // Returns whether a point is inside the rectangle.
    public boolean containsCursor(double x, double y) {
        return x >= this.x && x < this.getLimitX() && y >= this.y && y < this.getLimitY();
    }

    // Returns the horizontal center.
    public int getCenterX() {
        return this.x + (this.width / 2);
    }

    // Returns the vertical center.
    public int getCenterY() {
        return this.y + (this.height / 2);
    }

    // Returns a copy with a height.
    public Dim2i withHeight(int newHeight) {
        return new Dim2i(x, y, width, newHeight);
    }

    // Returns a copy with a width.
    public Dim2i withWidth(int newWidth) {
        return new Dim2i(x, y, newWidth, height);
    }

    // Returns a copy at an x.
    public Dim2i withX(int newX) {
        return new Dim2i(newX, y, width, height);
    }

    // Returns a copy at a y.
    public Dim2i withY(int newY) {
        return new Dim2i(x, newY, width, height);
    }

    // Returns whether another rectangle lies inside this one.
    public boolean canFitDimension(Dim2i anotherDim) {
        return this.x() <= anotherDim.x() && this.y() <= anotherDim.y() && this.getLimitX() >= anotherDim.getLimitX() && this.getLimitY() >= anotherDim.getLimitY();
    }

    // Returns whether the rectangles overlap.
    public boolean overlapsWith(Dim2i other) {
        return this.x() < other.getLimitX() && this.getLimitX() > other.x() && this.y() < other.getLimitY() && this.getLimitY() > other.y();
    }

    // Returns a copy moved by the origin of a parent.
    public Dim2i withParentOffset(Point2i parent) {
        return new Dim2i(parent.x() + x, parent.y() + y, width, height);
    }
}
