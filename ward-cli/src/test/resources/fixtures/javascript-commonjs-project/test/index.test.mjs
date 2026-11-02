import { expect, test } from "vitest";
import app from "../src/index.js";

test("adds", () => {
  expect(app.total(1, 2)).toBe(3);
});
