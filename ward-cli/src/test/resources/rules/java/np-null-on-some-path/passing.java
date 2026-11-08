// passes
final class Check {
  int test(String text, boolean flag) {
    if (text == null) {
      return 0;
    }
    return text.length();
  }

  int other(String text, boolean flag) {
    if (text == null) {
      flag = false;
    }
    if (flag) {
      return text.length();
    }
    return 0;
  }
}
