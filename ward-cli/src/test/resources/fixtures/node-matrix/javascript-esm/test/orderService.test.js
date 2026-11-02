import { describe, expect, it } from "vitest";
import { orderTotal, placeOrder } from "../src/service/orderService.js";

describe("placeOrder", () => {
  it("stores a positive order", () => {
    expect(placeOrder("a", 5)).toEqual({ id: "a", total: 5 });
    expect(orderTotal("a")).toBe(5);
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
