import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

const root = document.getElementById("root");
if (root === null) {
  throw new Error("the page has no #root element");
}

createRoot(root).render(<StrictMode>
    <h1>Vite React fixture</h1>
  </StrictMode>);
