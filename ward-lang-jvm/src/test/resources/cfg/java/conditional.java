class Conditional {
  String result;

  void run(boolean a) {
    result = a ? one() : two();
    done();
  }
}
