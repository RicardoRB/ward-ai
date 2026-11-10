import { describe, expect, it } from "vitest";
import { getTotal, postOrder } from "../src/controller/orderController.js";

const QUANTITY = 7;

describe("orderController", () => {
  it("confirms a placed order and reports its total", () => {
    expect(postOrder("c", QUANTITY)).toBe("placed c");
    expect(getTotal("c")).toBe(`total ${QUANTITY}`);
  });
});
