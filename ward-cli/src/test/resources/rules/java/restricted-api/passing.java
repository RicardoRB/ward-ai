














class Sample {
  void run(Api api) {
    api.dangerous();
  }

  @Warned
  void soft(Api api) {
    api.dangerous();
  }

  void other(Api api) {
    api.safe();
  }
}

class Api {
  void dangerous() {}

  void safe() {}
}
