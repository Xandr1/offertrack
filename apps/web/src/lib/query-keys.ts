export const queryKeys = {
  authMe: ["auth", "me"] as const,
  dashboardSummary: ["dashboard", "summary"] as const,
  applications: {
    list: () => ["applications"] as const,
    interviews: (applicationId: string) =>
      ["applications", applicationId, "interviews"] as const,
  },
};
