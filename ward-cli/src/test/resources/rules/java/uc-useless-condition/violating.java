class Ranges {
  void nested(int x) {
    if (x > 5) {
      if (x > 3) {
        use();
      }
    }
  }

  void negative(int y) {
    if (y < 0) {
      if (y >= 0) {
        use();
      }
    }
  }

  void conjunction(int z) {
    if (z > 10 && z > 2) {
      use();
    }
  }

  void otherBranch(int w) {
    if (w > 4) {
      use();
    } else {
      if (w > 9) {
        use();
      }
    }
  }

  void use() {}
}
