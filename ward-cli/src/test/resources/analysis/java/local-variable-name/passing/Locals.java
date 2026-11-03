final class Locals {
  int run() {
    int localX = 1;
    return localX;
  }
  Locals copy() {
    Locals local = this;
    return local;
  }
}
