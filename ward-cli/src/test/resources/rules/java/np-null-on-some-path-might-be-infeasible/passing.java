final class Check {
  int test(String a, String text, boolean flag) {
    if (a == null) {
      flag = true;
    }
    if (text == null) {
      flag = false;
    }
    return text.length();
  }
}

