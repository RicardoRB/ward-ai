import { expect, it } from "vitest";
import { total } from "../src/untested";

it("loads the module without calling it", () => {
  expect(typeof total).toBe("function");
});
