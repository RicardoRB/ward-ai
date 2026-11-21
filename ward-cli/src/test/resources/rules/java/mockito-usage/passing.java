final class T {
  void run(java.util.List<String> a, java.util.Random r, int n) {
    verify(a).clear();
    when(a.size()).thenReturn(1);
  }

  // A method of the project that happens to be named verify, with no Mockito type involved.
  void unknownType(String process) {
    verify(process);
  }

  void verify(String process) {}
}
