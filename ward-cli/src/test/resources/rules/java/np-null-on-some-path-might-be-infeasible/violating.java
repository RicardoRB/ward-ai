final class Check {
  int test(String a, String b, String c, String text, boolean flag) {
    if (a == null) {
      flag = true;
    }
    if (b == null) {
      flag = false;
    }
    if (c != null) {
      flag = true;
    }
    if (text == null) {
      flag = false;
    }
    return text.length();
  }
}

