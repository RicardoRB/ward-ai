


class Small implements Comparable<Small> {
  public int compareTo(Small o) { return 0; }
}

class Sub extends Base implements Comparable<Base> {
  public int compareTo(Base o) { return 0; }
}

class Gen<T extends Base> implements Comparable<T> {
  public int compareTo(T o) { return 0; }
}
