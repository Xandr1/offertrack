import path from "node:path";
import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";

const webRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(webRoot, "../..");

const nextConfig: NextConfig = {
  turbopack: {
    // Use the monorepo root so pnpm workspace dependencies are resolvable.
    root: repoRoot,
  },
};

export default nextConfig;
