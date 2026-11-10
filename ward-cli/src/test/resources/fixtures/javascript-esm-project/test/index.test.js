import { expect, test } from "vitest";
import { total } from "../src/index.js";

test("adds", () => {
  expect(total(1, 2)).toBe(total(2, 1));
});
