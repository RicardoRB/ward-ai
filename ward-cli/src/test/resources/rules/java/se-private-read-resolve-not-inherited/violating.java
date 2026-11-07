class Single implements java.io.Serializable {
  private Object readResolve() {
    return this;
  }
}
