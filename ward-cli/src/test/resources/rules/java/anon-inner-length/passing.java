class Passing {
  Runnable run() {
    return new Runnable() {
      public void run() {
        int value = 1;
      }
    };
  }
}
