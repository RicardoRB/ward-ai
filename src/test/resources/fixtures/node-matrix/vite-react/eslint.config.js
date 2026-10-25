// managed-by: ward preset=strict
import base from "./eslint.base.js";
import { defineConfig } from "eslint/config";
import tseslint from "typescript-eslint";

export default defineConfig(
  {
    ignores: [
      "eslint.base.*",
      ".next/",
      "out/",
      "build/",
      "dist/",
      "coverage/",
      ".ward/",
    ],
  },
  await base,
  tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    rules: {
      "@typescript-eslint/no-extraneous-class": [
        "error",
        { allowWithDecorator: true },
      ],
    },
  },
  {
    files: ["**/*.{js,mjs,cjs}"],
    extends: [tseslint.configs.disableTypeChecked],
  },
);
