final class Trim {
  String run(String text) {
    return text.trim();
  }

  String pick(int mode, String text) {
    return switch (mode) {
      case 1 -> text.trim();
      default -> text;
    };
  }
}
