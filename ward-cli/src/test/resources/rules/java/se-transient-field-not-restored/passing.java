final class T implements Serializable {
  private transient int cache;

  void a() {
    cache = 1;
  }

  void b() {
    cache = 2;
  }

  private void readObject(ObjectInputStream in) {
    cache = 0;
  }
}
