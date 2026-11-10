import { describe, expect, it } from "vitest";
import orderController from "../src/controller/orderController.js";

const { getTotal, postOrder } = orderController;

const QUANTITY = 7;

describe("orderController", () => {
  it("confirms a placed order and reports its total", () => {
    expect(postOrder("c", QUANTITY)).toBe("placed c");
    expect(getTotal("c")).toBe(`total ${QUANTITY}`);
  });

  it("passes the service's rejections through", () => {
    expect(() => postOrder("z", 0)).toThrow("total must be positive");
    expect(() => getTotal("missing")).toThrow("unknown order missing");
  });
});
