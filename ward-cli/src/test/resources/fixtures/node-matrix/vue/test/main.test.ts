import { describe, expect, it } from "vitest";

describe("main", () => {
  it("mounts the app on #app", async () => {
    document.body.innerHTML = '<div id="app"></div>';

    await import("../src/main");

    expect(document.querySelector("#app h1")?.textContent).toBe("You did it!");
  });
});
