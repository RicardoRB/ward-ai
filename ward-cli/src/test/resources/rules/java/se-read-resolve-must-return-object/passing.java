class Sample {
  private Object readResolve() {
    return this;
  }
}
