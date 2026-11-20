final class Trim {
  void run(String text) {
    text.trim();
  }

  void pick(int mode, String text) {
    switch (mode) {
      case 1 -> text.trim();
      default -> { }
    }
  }
}
