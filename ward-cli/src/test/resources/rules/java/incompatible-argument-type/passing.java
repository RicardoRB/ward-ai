











class Sample {
  boolean run(Box<String> box, String name) {
    return box.has(name);
  }
}

class Box<K> {
  boolean has(Object o) {
    return false;
  }
}
