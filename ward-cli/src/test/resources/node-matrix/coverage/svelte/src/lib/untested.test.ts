import { expect, it } from "vitest";
import { total } from "./untested";

it("loads the module without calling it", () => {
  expect(typeof total).toBe("function");
});
