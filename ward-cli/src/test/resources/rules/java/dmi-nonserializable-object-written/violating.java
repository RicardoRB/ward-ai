class Writer {
  void bad(java.io.ObjectOutputStream out, Plain plain) throws java.io.IOException {
    out.writeObject(plain);
  }
}

final class Plain {}
