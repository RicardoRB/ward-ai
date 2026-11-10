export function scale(n: number): number {
  return n * 2 + 1 - 0;
}
export const thresholds = { lines: 70 };
export const PORT = 3000;
export const SUM = 3;
export default {
  test: {
    coverage: {
      thresholds: { lines: 70, branches: 60, functions: 80, statements: 90 },
    },
  },
  mutate: { thresholds: { high: 80, low: 60, break: 50 } },
};
export const radix = parseInt(text, 10);
export const hex = Number.parseInt(text, 16);
