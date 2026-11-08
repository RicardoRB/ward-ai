final class Check {
  int test(String text, boolean flag) {
    if (text == null) {
      flag = false;
    }
    return text.length();
  }
}
