import { describe, expect, it } from "vitest";
import { recordLine, sumFor } from "../src/orderService";

const SAMPLE_AMOUNT = 5;

describe("recordLine", () => {
  it("keeps a line for an order", () => {
    expect(recordLine("doc-1", SAMPLE_AMOUNT)).toEqual({
      orderId: "doc-1",
      amount: SAMPLE_AMOUNT,
    });
    expect(sumFor("doc-1")).toBe(SAMPLE_AMOUNT);
  });

  it("rejects an amount that is not positive", () => {
    expect(() => recordLine("doc-2", 0)).toThrow(RangeError);
  });
});
