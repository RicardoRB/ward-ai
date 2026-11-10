import express from "express";

/** The HTTP app of the fixture: one route that reports the service is up. */
export const app = express();

app.get("/health", (_request, response) => {
  response.json({ status: "ok" });
});
