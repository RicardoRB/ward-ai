final class NeedBracesViolations {
  void run(boolean a) {
    if (a) run(false);
    for (int i = 0; i < 2; i++) run(a);
    while (a) a = false;
    if (a) {
      run(a);
    } else run(false);
    do run(a); while (a);
  }
}
