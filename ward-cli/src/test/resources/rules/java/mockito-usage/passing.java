final class T {
  void run(java.util.List<String> a, java.util.Random r, int n) {
    verify(a).clear();
    when(a.size()).thenReturn(1);
  }
}
