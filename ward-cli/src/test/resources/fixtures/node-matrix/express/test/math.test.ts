import { describe, expect, it } from "vitest";
import { add, average, clamp } from "../src/lib/math";

const FIVE = 5;
const TEN = 10;
const THREE = 3;
const FOUR = 4;
const SEVEN = 7;
const FORTY_TWO = 42;
const MINUS_THREE = -3;
const TWO_AND_A_HALF = 2.5;

describe("add", () => {
  it("adds two numbers", () => {
    expect(add(2, THREE)).toBe(FIVE);
    expect(add(-1, 1)).toBe(0);
  });
});

describe("clamp", () => {
  it("keeps a value inside the bounds", () => {
    expect(clamp(FIVE, 0, TEN)).toBe(FIVE);
  });

  it("raises a value below the minimum", () => {
    expect(clamp(MINUS_THREE, 0, TEN)).toBe(0);
  });

  it("lowers a value above the maximum", () => {
    expect(clamp(FORTY_TWO, 0, TEN)).toBe(TEN);
  });

  it("accepts equal bounds", () => {
    expect(clamp(SEVEN, THREE, THREE)).toBe(THREE);
  });

  it("rejects bounds in the wrong order", () => {
    expect(() => clamp(1, TEN, 0)).toThrow("min must not exceed max");
  });
});

describe("average", () => {
  it("averages the values", () => {
    expect(average([1, 2, THREE, FOUR])).toBe(TWO_AND_A_HALF);
  });

  it("rejects an empty list", () => {
    expect(() => average([])).toThrow("average needs at least one value");
  });
});
