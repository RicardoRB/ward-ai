export class Point {
  private x: number = 0;

  constructor(x: number) {
    this.x = x;
  }

  move(x: number): void {
    this.x = x;
  }
}
