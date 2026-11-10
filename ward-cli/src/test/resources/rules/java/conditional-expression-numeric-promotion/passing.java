final class T {
  Object run(boolean flag, Integer i1, Integer i2) {
    Object x = flag ? i1 : i2;
    return flag ? i1 : "c";
  }
}
