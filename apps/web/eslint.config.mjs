import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const __dirname = dirname(fileURLToPath(import.meta.url));

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    files: ["**/*.{ts,tsx,mts}"],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      "@typescript-eslint/no-unused-vars": "error",
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-floating-promises": [
        "error",
        { ignoreVoid: true },
      ],
      "no-restricted-syntax": [
        "error",
        {
          selector:
            "CallExpression[callee.type='MemberExpression'][callee.object.type='Identifier'][callee.object.name=/^(localStorage|sessionStorage)$/][callee.property.type='Identifier'][callee.property.name=/^(setItem|getItem|removeItem)$/][arguments.0.type='Literal'][arguments.0.value=/token|jwt|auth|access|refresh/i]",
          message:
            "Do not store auth tokens in browser storage. Use HttpOnly cookie-based auth instead.",
        },
        {
          selector:
            "CallExpression[callee.type='MemberExpression'][callee.object.type='MemberExpression'][callee.object.object.type='Identifier'][callee.object.object.name='window'][callee.object.property.type='Identifier'][callee.object.property.name=/^(localStorage|sessionStorage)$/][callee.property.type='Identifier'][callee.property.name=/^(setItem|getItem|removeItem)$/][arguments.0.type='Literal'][arguments.0.value=/token|jwt|auth|access|refresh/i]",
          message:
            "Do not store auth tokens in browser storage. Use HttpOnly cookie-based auth instead.",
        },
        {
          selector:
            "CallExpression[callee.type='MemberExpression'][callee.object.type='Identifier'][callee.object.name=/^(localStorage|sessionStorage)$/][callee.property.type='Identifier'][callee.property.name=/^(setItem|getItem|removeItem)$/]:not([arguments.0.type='Literal'])",
          message:
            "Do not use dynamic browser storage keys. Use an explicit typed storage helper if browser storage is needed.",
        },
        {
          selector:
            "CallExpression[callee.type='MemberExpression'][callee.object.type='MemberExpression'][callee.object.object.type='Identifier'][callee.object.object.name='window'][callee.object.property.type='Identifier'][callee.object.property.name=/^(localStorage|sessionStorage)$/][callee.property.type='Identifier'][callee.property.name=/^(setItem|getItem|removeItem)$/]:not([arguments.0.type='Literal'])",
          message:
            "Do not use dynamic browser storage keys. Use an explicit typed storage helper if browser storage is needed.",
        },
      ],
    },
  },
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "coverage/**",
    "next-env.d.ts",
    "playwright-report/**",
    "test-results/**",
    "blob-report/**",
  ]),
]);

export default eslintConfig;
