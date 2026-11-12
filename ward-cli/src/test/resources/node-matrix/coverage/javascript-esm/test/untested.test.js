import { expect, it } from "vitest";
import { total } from "../src/service/untested.js";

it("loads the module without calling it", () => {
  expect(typeof total).toBe("function");
});
