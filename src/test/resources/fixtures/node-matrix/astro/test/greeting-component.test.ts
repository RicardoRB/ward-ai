import { experimental_AstroContainer as AstroContainer } from "astro/container";
import { describe, expect, it } from "vitest";
import Greeting from "../src/components/Greeting.astro";

describe("Greeting.astro", () => {
  it("renders the greeting for the name prop", async () => {
    const container = await AstroContainer.create();
    const html = await container.renderToString(Greeting, {
      props: { name: "Ada" },
    });

    expect(html).toContain("Hello, Ada");
  });
});
