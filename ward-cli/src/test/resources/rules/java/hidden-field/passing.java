class Passing {
  private int count;

  void run(int size) {
    int local = size;
    System.out.println(local + count);
  }
}

class Shapes {
  private int size;

  interface Sizer {
    int measure(int size);
  }

  static Sizer sizer() {
    return new Sizer() {
      @Override
      public int measure(int size) {
        return size;
      }
    };
  }
}
