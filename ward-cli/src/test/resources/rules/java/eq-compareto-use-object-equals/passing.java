class Sample implements Comparable<Sample> {
  public int compareTo(Sample other) {
    return 0;
  }
  public boolean equals(Object other) {
    return other instanceof Sample;
  }
}
