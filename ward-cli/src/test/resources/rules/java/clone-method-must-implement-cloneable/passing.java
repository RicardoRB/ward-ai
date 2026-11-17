class Copy implements Cloneable {
  Copy clone() {
    return this;
  }
}

class Zone extends Base {
  Zone clone() {
    return this;
  }
}

class Named {
  void test_clone() {
  }
}
