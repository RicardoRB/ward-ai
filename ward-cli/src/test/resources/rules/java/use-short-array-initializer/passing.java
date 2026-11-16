class Passing {
  int[] a = {1, 2};
  int[] b = new int[3];
  void run() {
    int[] c;
    c = new int[] {1};
  }
  Object[] nested = {null, new int[] {3, 6}};
  int size() {
    return java.util.Arrays.asList(new Object[] {1, new int[] {2}}).size();
  }
}
