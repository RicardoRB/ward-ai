class Sample implements Cloneable {
  public Object clone() {
    reset();
    return this;
  }

  void reset() {}
}

