class Calculator {
  private int total;

  public int add(int value) {
    return total + value;
  }

  public int subtract(int value) {
    return total - value;
  }

  public int reset() {
    total = 0;
    return total;
  }
}

class Row {
  public String a;
  public String b;
  public String c;
  public String d;

  static Row of(String value) {
    final Row row = new Row();
    row.a = value;
    return row;
  }
}
