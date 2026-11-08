
class Same extends Base {
  static int counter;

  void touch() {}
}

class Own extends Base {
  int extra;

  public boolean equals(Object o) {
    return true;
  }
}

class Fresh {
  int field;
}
