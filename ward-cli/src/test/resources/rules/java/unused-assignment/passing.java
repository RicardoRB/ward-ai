class Used {
  int compute() {
    return 1;
  }

  int used() {
    int x = compute();
    return x;
  }
}

class Finally {
  void run() {}

  void end(boolean failed) {}

  Finally begin() {
    boolean completed = false;
    try {
      run();
      completed = true;
      return this;
    } finally {
      end(!completed);
    }
  }
}
