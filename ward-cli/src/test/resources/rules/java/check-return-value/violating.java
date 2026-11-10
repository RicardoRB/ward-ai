final class T {
  Object run(Api api) {
    api.compute();
    Object used = api.compute();
    return used;
  }
}
