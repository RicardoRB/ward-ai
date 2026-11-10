import assert from "node:assert/strict";
import { test } from "node:test";
import app from "../src/index.js";

test("adds", () => {
  assert.equal(app.total(1, 2), app.total(2, 1));
});
