


class Good implements Marker {
  public Class<? extends Annotation> annotationType() { return Marker.class; }
  public boolean equals(Object o) { return o instanceof Good; }
  public int hashCode() { return 0; }
}

class Other {
}
