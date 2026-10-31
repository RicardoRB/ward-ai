// managed-by: ward preset=strict
import js from "@eslint/js";
import { defineConfig } from "eslint/config";
import globals from "globals";

export default defineConfig(
  { ignores: ["dist/", "coverage/", ".next/", "out/", "build/", ".ward/"] },
  js.configs.recommended,
  { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
  {
    files: ["**/*.jsx"],
    languageOptions: { parserOptions: { ecmaFeatures: { jsx: true } } },
  },
  { files: ["**/*.js"], languageOptions: { sourceType: "commonjs" } },
);
