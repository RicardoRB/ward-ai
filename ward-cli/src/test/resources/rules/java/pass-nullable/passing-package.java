package demo;

@NullUnmarked
class Caller {
  int run() {
    return length(null);
  }

  int length(String text) {
    return text.length();
  }
}
