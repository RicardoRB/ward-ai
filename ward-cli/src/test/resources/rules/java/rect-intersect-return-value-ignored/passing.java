
final class Bounds {
  boolean clip(android.graphics.Rect bounds, android.graphics.Rect other) {
    return bounds.intersect(other);
  }
}
