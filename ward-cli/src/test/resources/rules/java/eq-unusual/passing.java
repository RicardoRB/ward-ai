class Fine {
  int id;

  @Override
  public boolean equals(Object o) {
    if (o instanceof Fine) {
      return id == ((Fine) o).id;
    }
    return false;
  }
}

class Cast {
  int id;

  @Override
  public boolean equals(Object o) {
    return id == ((Cast) o).id;
  }
}

class ByClass {
  @Override
  public boolean equals(Object o) {
    return o != null && getClass() == o.getClass();
  }
}

class Delegate {
  String s;

  @Override
  public boolean equals(Object o) {
    return s.equals(o.toString());
  }
}

class Identity {
  @Override
  public boolean equals(Object o) {
    return this == o;
  }
}

class Super {
  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }
}

class Always {
  @Override
  public boolean equals(Object o) {
    return true;
  }
}

class Hidden {
  boolean equals(Object o) {
    return o != null && hashCode() == o.hashCode();
  }
}

class Other {
  public boolean equals(Other o) {
    return hashCode() == o.hashCode();
  }
}
