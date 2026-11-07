class Cloner implements Cloneable {
  public Object clone() {
    return new Cloner();
  }
}
