final class T implements Serializable {
  private Object held;
  private transient Object cache;

  void keep() {
    // ok
    this.held = "text";
    this.cache = new Thread();
  }
}
