class Holder implements java.io.Serializable {
  private transient int cache;
}

class Magnitude extends Number {
  private transient String text;
}

class Recorder extends java.util.GregorianCalendar {
  private transient int pending;
}

class Outer {
  static final class Inner implements java.io.Serializable {
    private transient int cache;
  }
}
