class Copy implements Cloneable {
  Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}

class Named {
  void test_clone() {
  }
}
