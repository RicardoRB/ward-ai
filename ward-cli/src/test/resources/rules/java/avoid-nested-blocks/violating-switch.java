final class SwitchNestedBlock {
  void run(int a) {
    switch (a) {
      case 1:
        {
          run(a);
        }
      default:
        break;
    }
  }
}
