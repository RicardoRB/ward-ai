class KeyOk {
  int id;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof KeyOk)) {
      return false;
    }
    KeyOk other = (KeyOk) o;
    return id == other.id;
  }
}

class Same {
  int id;

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Same other = (Same) o;
    return id == other.id;
  }
}

class Plain {
  public boolean equals(Object o) {
    Object other = (Object) o;
    return other == this;
  }

  public boolean equals(Plain p) {
    Plain other = (Plain) p;
    return other == this;
  }
}
