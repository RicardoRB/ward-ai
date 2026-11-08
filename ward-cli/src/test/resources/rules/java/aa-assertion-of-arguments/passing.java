class ChecksOk {
  int size;

  private void hidden(int size) {
    assert size > 0;
  }

  public void check() {
    int local = size;
    assert local > 0;
    assert size > 0;
  }

  public void set(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException();
    }
  }
}
