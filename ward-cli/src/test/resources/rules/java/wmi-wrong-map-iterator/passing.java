final class Sample {
  int sum(java.util.Map<String, Integer> map) {
    int total = 0;
    for (java.util.Map.Entry<String, Integer> e : map.entrySet()) {
      total += e.getValue();
    }
    return total;
  }
}
