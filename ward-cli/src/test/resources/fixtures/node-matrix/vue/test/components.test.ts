import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import App from "../src/App.vue";
import HelloWorld from "../src/components/HelloWorld.vue";
import TheWelcome from "../src/components/TheWelcome.vue";
import WelcomeItem from "../src/components/WelcomeItem.vue";
import IconCommunity from "../src/components/icons/IconCommunity.vue";
import IconDocumentation from "../src/components/icons/IconDocumentation.vue";
import IconEcosystem from "../src/components/icons/IconEcosystem.vue";
import IconSupport from "../src/components/icons/IconSupport.vue";
import IconTooling from "../src/components/icons/IconTooling.vue";

describe("components", () => {
  it("renders the greeting from the message prop", () => {
    const wrapper = mount(HelloWorld, { props: { msg: "Hello matrix" } });

    expect(wrapper.get("h1").text()).toBe("Hello matrix");
  });

  it("renders each welcome item with its heading and icon slot", () => {
    const wrapper = mount(WelcomeItem, {
      slots: { icon: '<span class="icon">i</span>', heading: "Heading" },
    });

    expect(wrapper.get("h3").text()).toBe("Heading");
    expect(wrapper.find(".icon").exists()).toBe(true);
  });

  it("lists the five welcome items", () => {
    const wrapper = mount(TheWelcome);

    expect(wrapper.findAll(".item")).toHaveLength(5);
    expect(wrapper.text()).toContain("Documentation");
  });

  it("composes the header and the welcome section", () => {
    const wrapper = mount(App);

    expect(wrapper.text()).toContain("You did it!");
    expect(wrapper.findAll(".item")).toHaveLength(5);
  });

  it.each([
    ["community", IconCommunity],
    ["documentation", IconDocumentation],
    ["ecosystem", IconEcosystem],
    ["support", IconSupport],
    ["tooling", IconTooling],
  ])("renders the %s icon as an svg", (_name, icon) => {
    expect(mount(icon).find("svg").exists()).toBe(true);
  });
});
