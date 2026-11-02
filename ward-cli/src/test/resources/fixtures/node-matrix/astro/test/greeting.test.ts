import { describe, expect, it } from "vitest";
import { greeting } from "../src/lib/greeting";

describe("greeting", () => {
  it("greets a name", () => {
    expect(greeting(" Ada ")).toBe("Hello, Ada");
  });

  it("greets a stranger when the name is blank", () => {
    expect(greeting("   ")).toBe("Hello, stranger");
  });
});
