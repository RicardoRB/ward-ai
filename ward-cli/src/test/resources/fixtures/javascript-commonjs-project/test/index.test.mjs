import { expect, test } from "vitest";
import app from "../src/index.js";

test("adds", () => {
  expect(app.total(1, 2)).toBe(app.total(2, 1));
});
