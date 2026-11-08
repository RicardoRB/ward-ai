class Sample implements Cloneable {
  public Object clone() {
    reset();
    return this;
  }

  final void reset() {}
}

