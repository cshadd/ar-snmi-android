package io.github.cshadd.ar_snfmi_android;

import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;

public final class CRect
        extends Rect {
    public CRect() {
        super();
    }

    public CRect(Point p, Size s) { super(p, s); }

    public CRect(Point p1, Point p2) { super(p1, p2); }

    public CRect(Rect r) { super(r.x, r.y, r.width, r.height); }

    public CRect(double[] vals) {
        super(vals);
    }

    public CRect(int x, int y, int width, int height) { super(x, y, width, height); }

    public Point bl() { return new Point(x, y + height); }

    public Point tr() { return new Point(x + width, y); }
}