export class Counter {
  private count: number = 0;

  bump(count: number): void {
    this.count = count;
  }
}
