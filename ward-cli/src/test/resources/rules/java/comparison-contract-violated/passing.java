
class Fine implements Comparable<Fine> {
  int n;
  static final int LESS = -1;

  public int compareTo(Fine o) {
    return n < o.n ? -1 : n == o.n ? 0 : 1;
  }
}

class OneSided implements Comparable<OneSided> {
  public int compareTo(OneSided o) {
    return 0;
  }
}

class Computed implements Comparator<String> {
  public int compare(String a, String b) {
    return a.length() - b.length();
  }
}

class Plain {
  public int compareTo(Plain o) {
    return 1;
  }
}

class Dynamic implements Comparable<Dynamic> {
  int rank;

  public int compareTo(Dynamic o) {
    return rank;
  }
}
