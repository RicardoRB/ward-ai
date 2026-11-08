class Version implements Comparable<Version> {
  int n;

  public int compareTo(Version o) {
    return n < o.n ? -1 : 1;
  }
}

class ByLength implements Comparator<String> {
  public int compare(String a, String b) {
    if (a.length() > b.length()) {
      return 1;
    }
    return 0;
  }
}

class Flag implements Comparable<Flag> {
  boolean up;

  public int compareTo(Flag o) {
    return up ? -1 : 1;
  }
}

class Const implements Comparable<Const> {
  static final int LESS = -1;
  static final int MORE = 1;

  public int compareTo(Const o) {
    return LESS;
  }
}
