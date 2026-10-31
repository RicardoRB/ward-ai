// managed-by: ward preset=strict
export default {
  testRunner: "vitest",
  reporters: ["json", "clear-text", "progress"],
  thresholds: {
    high: 80,
    low: 60,
    break: 60,
  },
};
