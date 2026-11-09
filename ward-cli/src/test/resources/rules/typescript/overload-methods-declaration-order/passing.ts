export class Shape {
  area(): number;
  area(value?: number): number {
    return value ?? 0;
  }
  perimeter(): number {
    return 0;
  }
}
