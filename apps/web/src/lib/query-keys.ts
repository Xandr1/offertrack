import type {
  ApplicationsBoardParams,
  ApplicationsListParams,
} from "./api/types";

export const queryKeys = {
  authMe: ["auth", "me"] as const,
  emailVerification: (token: string) => ["auth", "email-verification", token] as const,
  dashboardSummary: ["dashboard", "summary"] as const,
  settings: ["settings"] as const,
  applications: {
    list: (params?: ApplicationsListParams) =>
      params ? (["applications", "list", params] as const) : (["applications", "list"] as const),
    board: (params?: ApplicationsBoardParams) =>
      params === undefined
        ? (["applications", "board"] as const)
        : (["applications", "board", params] as const),
    detail: (applicationId: string) =>
      ["applications", "detail", applicationId] as const,
    interviews: (applicationId: string) =>
      ["applications", applicationId, "interviews"] as const,
  },
};
