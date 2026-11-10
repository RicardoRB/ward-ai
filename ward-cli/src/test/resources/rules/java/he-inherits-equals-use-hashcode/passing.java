
class Concrete extends Plain {
}

class Hashed extends Shape {
}

class Own extends Shape {
  public int hashCode() {
    return 1;
  }
}
