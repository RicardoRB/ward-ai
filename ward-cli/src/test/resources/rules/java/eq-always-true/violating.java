final class Always {
  public boolean equals(Object other) {
    return true;
  }
}

final class Holder {
  void test() {
    class Local {
      public boolean equals(Object other) {
        return true;
      }
    }
  }
}
