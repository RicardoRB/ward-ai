final class Passing {
  void run(@CompileTimeConstant String name, final @CompileTimeConstant String other, int count) {
    count = 1;
  }

  void keep(@CompileTimeConstant String name) {}
}
