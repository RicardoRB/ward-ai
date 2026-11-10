import { describe, expect, it } from "vitest";
import { orderTotal, placeOrder } from "@repo/ui/order";

const AMOUNT = 5;

describe("placeOrder", () => {
  it("stores a positive order", () => {
    expect(placeOrder("a", AMOUNT)).toEqual({ id: "a", total: AMOUNT });
    expect(orderTotal("a")).toBe(AMOUNT);
  });

  it("rejects a total that is not positive", () => {
    expect(() => placeOrder("b", 0)).toThrow("total must be positive");
  });
});

describe("orderTotal", () => {
  it("rejects an unknown order", () => {
    expect(() => orderTotal("missing")).toThrow("unknown order missing");
  });
});
