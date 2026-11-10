final class Passing {
  void run(boolean flag, Object[] array) {


    print(flag ? array : new Object[] {1});
  }
  void print(Object... values) {
  }
}
