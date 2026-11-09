export class Shape {
  area(): number;
  perimeter(): number {
    return 0;
  }
  area(value?: number): number {
    return value ?? 0;
  }
}
