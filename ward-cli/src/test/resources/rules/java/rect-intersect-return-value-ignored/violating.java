final class Bounds {
  void clip(android.graphics.Rect bounds, android.graphics.Rect other) {
    bounds.intersect(other);
  }
}
