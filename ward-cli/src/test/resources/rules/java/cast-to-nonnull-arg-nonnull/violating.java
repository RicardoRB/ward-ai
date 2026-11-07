class Casts {
  String first() {
    return castToNonNull("text");
  }

  Object second() {
    return NullnessUtil.castToNonNull(new Object());
  }

  Object third() {
    return castToNonNull(this);
  }
}
