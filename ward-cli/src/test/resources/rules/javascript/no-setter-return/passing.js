class C {
  set a(v) {
    this.x = v;
    return;
  }
  set c(v) {
    const f = () => {
      return v;
    };
    this.x = f;
  }
  get b() {
    return 1;
  }
}
