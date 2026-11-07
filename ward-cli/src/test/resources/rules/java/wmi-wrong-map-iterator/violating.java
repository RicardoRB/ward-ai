final class Sample {
  int sum(java.util.Map<String, Integer> map) {
    int total = 0;
    for (String key : map.keySet()) {
      total += map.get(key);
    }
    return total;
  }
}
