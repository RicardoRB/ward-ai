export class Counter {
  private count = 0;

  bump(step) {
    this.count = this.count + step;
  }
}
