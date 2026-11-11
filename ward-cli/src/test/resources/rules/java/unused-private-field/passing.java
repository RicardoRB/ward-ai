class Fields {
  private static final long serialVersionUID = 1L;
  private int used;
  @Deprecated private int annotated;

  int read() {
    return used;
  }
}
