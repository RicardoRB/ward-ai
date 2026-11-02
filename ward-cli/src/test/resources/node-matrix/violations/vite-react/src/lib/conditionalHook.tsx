import { useState } from "react";

export function Conditional({ on }: { on: boolean }) {
  if (on) {
    useState(0);
  }
  return null;
}
