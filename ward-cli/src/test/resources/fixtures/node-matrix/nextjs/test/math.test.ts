import { describe, expect, it } from "vitest";
import { add, average, clamp } from "../src/lib/math";

describe("add", () => {
  it("adds two numbers", () => {
    expect(add(2, 3)).toBe(5);
    expect(add(-1, 1)).toBe(0);
  });
});

describe("clamp", () => {
  it("keeps a value inside the bounds", () => {
    expect(clamp(5, 0, 10)).toBe(5);
  });

  it("raises a value below the minimum", () => {
    expect(clamp(-3, 0, 10)).toBe(0);
  });

  it("lowers a value above the maximum", () => {
    expect(clamp(42, 0, 10)).toBe(10);
  });

  it("accepts equal bounds", () => {
    expect(clamp(7, 3, 3)).toBe(3);
  });

  it("rejects bounds in the wrong order", () => {
    expect(() => clamp(1, 10, 0)).toThrow("min must not exceed max");
  });
});

describe("average", () => {
  it("averages the values", () => {
    expect(average([1, 2, 3, 4])).toBe(2.5);
  });

  it("rejects an empty list", () => {
    expect(() => average([])).toThrow("average needs at least one value");
  });
});
