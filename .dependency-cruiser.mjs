// managed-by: ward
export default {
  forbidden: [
    // controllers must not depend on repositories.
    {
      name: "layered-architecture",
      severity: "error",
      from: { path: "(^|/)(controller)/" },
      to: { path: "(^|/)(repository)/" },
    },
    {
      name: "no-circular",
      severity: "error",
      from: {},
      to: { circular: true },
    },
  ],
  options: {
    doNotFollow: { path: "node_modules" },
    exclude: {
      path: "(^|/)(node_modules|dist|build|coverage|reports|\\.ward|\\.stryker-tmp)/",
    },
  },
};
