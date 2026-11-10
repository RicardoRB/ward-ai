export class Counter {
  private count: number = 0;

  bump(step: number): void {
    this.count = this.count + step;
  }
}
