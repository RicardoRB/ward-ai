import { expect, it } from "vitest";
import { total } from "../src/lib/untested";

it("loads the module without calling it", () => {
  expect(typeof total).toBe("function");
});
