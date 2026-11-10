package dataflow;

class Switches {
  int pick(int k) {
    int x = 0;
    switch (k) {
      case 1:
        x = 1;
        break;
      default:
        break;
    }
    return x;
  }

  int noDefault(int k) {
    int y = 0;
    switch (k) {
      case 1:
        y = 1;
        break;
    }
    return y;
  }
}
