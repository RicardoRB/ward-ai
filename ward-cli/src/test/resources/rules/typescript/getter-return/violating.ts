class C {
  get c(): number {
    console.log(1);
    return 1;
  }
  get d(): number {
    console.log(2);
  }
  get e(): number {
    return;
  }
  get f(): number {
    if (c) {
      return 1;
    }
    return 2;
  }
}
