/** @jest-environment jsdom */

import React from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Application, ApplicationInterview } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { saveApplicationWithInterviews } from "../services/application-save-service";
import { useApplicationSave } from "./use-application-save";

jest.mock("../services/application-save-service", () => ({
  ...jest.requireActual("../services/application-save-service"),
  saveApplicationWithInterviews: jest.fn(),
}));

const mockedSaveApplicationWithInterviews =
  saveApplicationWithInterviews as jest.MockedFunction<
    typeof saveApplicationWithInterviews
  >;

const application: Application = {
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00.000Z",
  id: "app-1",
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage: "interviewing",
  updatedAt: "2026-01-02T00:00:00.000Z",
  workMode: null,
};

const interviews: ApplicationInterview[] = [
  {
    applicationId: "app-1",
    createdAt: "2026-01-02T00:00:00.000Z",
    followedUpAt: null,
    id: "int-1",
    scheduledAt: "2026-01-10T10:00:00.000Z",
    status: "scheduled",
    type: "technical",
    updatedAt: "2026-01-02T00:00:00.000Z",
  },
];

describe("useApplicationSave", () => {
  beforeEach(() => {
    mockedSaveApplicationWithInterviews.mockReset();
  });

  it("writes the save response to detail caches and preserves feature invalidation", async () => {
    mockedSaveApplicationWithInterviews.mockResolvedValue({
      application,
      applicationId: application.id,
      interviews,
      mode: "edit",
    });
    const queryClient = new QueryClient({
      defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
    });
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children);
    const { result } = renderHook(
      () =>
        useApplicationSave({
          onMutationError: jest.fn(),
          queryClient,
        }),
      { wrapper },
    );

    act(() => {
      result.current.saveApplicationMutation.mutate({
        applicationId: application.id,
        form: {
          appliedAt: "",
          companyName: application.companyName,
          jobUrl: "",
          location: "",
          notes: "",
          positionTitle: application.positionTitle,
          stage: application.stage,
          workMode: "",
        },
        mode: "edit",
        rows: [],
      });
    });

    await waitFor(() =>
      expect(result.current.saveApplicationMutation.isSuccess).toBe(true),
    );

    expect(
      queryClient.getQueryData(
        queryKeys.applications.interviews(application.id),
      ),
    ).toEqual(interviews);
    expect(
      queryClient.getQueryData(queryKeys.applications.detail(application.id)),
    ).toEqual(application);
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
  });
});
