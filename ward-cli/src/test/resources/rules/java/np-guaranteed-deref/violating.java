final class Check {
  void test(String text, boolean flag) {
    if (text == null) {
      flag = false;
    }
    // Both paths dereference text on this one line, so the line is the same whichever one a tool names.
    if (flag) { text.trim(); } else { text.strip(); }
  }
}
