final class Violating {
  void run(@CompileTimeConstant String name) {
    name = name + "x";
  }
}
