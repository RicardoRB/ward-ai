package dev.ward.analysis.domain.openapi.breaking;

/** Which way a change hurts: a request that got stricter, or a response that got looser. */
enum Direction {
  /** Clients send data: a narrower schema rejects what they send. */
  REQUEST,
  /** Servers send data: a wider schema returns what clients do not expect. */
  RESPONSE
}
