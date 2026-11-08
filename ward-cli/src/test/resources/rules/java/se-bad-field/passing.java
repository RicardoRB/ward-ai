final class T implements Serializable {
  private transient Thread worker;
  private int n;
  private String s;
}

final class U implements Serializable {
  private Thread worker;

  private void writeObject(ObjectOutputStream out) {}
}
