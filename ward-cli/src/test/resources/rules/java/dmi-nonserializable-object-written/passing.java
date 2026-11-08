class WriterOk {
  void good(java.io.ObjectOutputStream out, Data data, Open open, Mystery mystery)
      throws java.io.IOException {
    out.writeObject(data);
    out.writeObject(open);
    out.writeObject(mystery);
  }
}

final class Data implements java.io.Serializable {}

class Open {}
