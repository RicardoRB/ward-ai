import { describe, expect, it } from "vitest";
import { orderTotal, placeOrder } from "../src/orderService";

const WEB_TOTAL = 9;

describe("web order service", () => {
  it("reads back the total of an order it placed", () => {
    expect(placeOrder("web-1", WEB_TOTAL).total).toBe(WEB_TOTAL);
    expect(orderTotal("web-1")).toBe(WEB_TOTAL);
  });

  it("names the order when it is unknown", () => {
    expect(() => orderTotal("web-missing")).toThrow("unknown order web-missing");
  });
});
