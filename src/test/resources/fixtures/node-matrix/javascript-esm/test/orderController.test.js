import { describe, expect, it } from "vitest";
import { getTotal, postOrder } from "../src/controller/orderController.js";

describe("orderController", () => {
  it("confirms a placed order and reports its total", () => {
    expect(postOrder("c", 7)).toBe("placed c");
    expect(getTotal("c")).toBe("total 7");
  });
});
