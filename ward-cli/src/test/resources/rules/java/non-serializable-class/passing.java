






class Sample implements java.io.Serializable {
  Helper helper;
  String name;
}

class Custom implements java.io.Serializable {
  Helper helper;

  void writeObject(Object out) {}
}

class Plain {
  Helper helper;
}
