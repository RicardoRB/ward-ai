import { expect, test } from "vitest";
import { total } from "../src/index.js";

test("adds", () => {
  expect(total(1, 2)).toBe(3);
});
