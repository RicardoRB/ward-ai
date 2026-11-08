class Sample {
  void run(Api api) {
    api.dangerous();
  }

  @Allowed
  void permitted(Api api) {
    api.dangerous();
  }
}

class Api {
  void dangerous() {}
}
