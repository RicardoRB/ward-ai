class Ranges {
  void notDecided(int x) {
    if (x > 5) {
      if (x > 7) {
        use();
      }
    }
  }

  void assigned(int x) {
    if (x > 5) {
      x = 0;
      if (x > 3) {
        use();
      }
    }
  }

  void otherVariable(int x, int y) {
    if (x > 5) {
      if (y > 3) {
        use();
      }
    }
  }

  void field() {
    if (count > 5) {
      if (count > 3) {
        use();
      }
    }
  }

  void incremented(int x) {
    if (x > 5) {
      x++;
      if (x > 3) {
        use();
      }
    }
  }

  private int count;

  void use() {}
}
