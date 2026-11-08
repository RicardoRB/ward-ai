class Sample {
  boolean run(Box<String> box, Integer key) {
    return box.has(key);
  }
}

class Box<K> {
  boolean has(Object o) {
    return false;
  }
}
