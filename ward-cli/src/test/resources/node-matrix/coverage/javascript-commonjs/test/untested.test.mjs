import { expect, it } from "vitest";
import untested from "../src/service/untested.js";

const { total } = untested;

it("loads the module without calling it", () => {
  expect(typeof total).toBe("function");
});
