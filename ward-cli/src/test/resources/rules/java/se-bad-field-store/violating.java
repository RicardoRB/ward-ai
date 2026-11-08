final class T implements Serializable {
  private Object held;

  void keep() {
    this.held = new Thread();
  }
}
