class TernaryOk {
  Integer same(boolean flag, Integer count, Integer second) {
    return flag ? count : second;
  }

  int unboxed(boolean flag, Integer count, int plain) {
    return flag ? count : plain;
  }

  double unknown(boolean flag, Object count, double ratio) {
    return flag ? count : ratio;
  }
}
