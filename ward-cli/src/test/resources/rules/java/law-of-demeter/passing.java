package sample;

class Sample {
  void run(Other other) {
    use(other.get());
    this.get().use();
  }
}
