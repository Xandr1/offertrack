/** @jest-environment jsdom */

import React, { type FormEvent } from "react";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createApplicationDraft, type ApplicationDraftResponse } from "@/lib/api";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { queryKeys } from "@/lib/query-keys";
import { useApplicationModalController } from "./use-application-modal-controller";
import { useCreateApplicationWithAiFlow } from "./use-create-application-with-ai-flow";

jest.mock("next/navigation", () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  createApplicationDraft: jest.fn(),
}));

const requestDraft = jest.mocked(createApplicationDraft);
const url = "https://example.com/jobs/123";
const draft: ApplicationDraftResponse = {
  companyName: "Acme",
  positionTitle: "Engineer",
  jobUrl: url,
  location: null,
  workMode: null,
  stage: "initial",
  notes: null,
  interviews: [],
  warnings: [],
};

describe("AI draft client cache", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-07-12T12:00:00Z"));
    requestDraft.mockReset().mockResolvedValue(draft);
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
  });

  afterEach(() => {
    queryClient.clear();
    jest.useRealTimers();
  });

  const setup = () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const hook = renderHook(() => {
      const modalController = useApplicationModalController();
      const flow = useCreateApplicationWithAiFlow({
        modalController,
        clearPageError: jest.fn(),
        prepareCreateApplicationModal: jest.fn(),
      });
      return { flow, modalController };
    }, { wrapper });
    const submit = async (jobUrl = url) => {
      act(() => hook.result.current.flow.openCreateApplicationModal());
      act(() => hook.result.current.flow.setCreateWithAiJobUrl(jobUrl));
      await act(async () => {
        await hook.result.current.flow.handleCreateWithAiSubmit({
          preventDefault: jest.fn(),
        } as unknown as FormEvent<HTMLFormElement>);
      });
    };
    return { ...hook, submit };
  };

  it("reuses successful results by normalized URL across modal and hook lifetimes", async () => {
    const first = setup();
    await first.submit("  example.com/jobs/123  ");
    expect(requestDraft).toHaveBeenCalledWith({ jobUrl: url });
    first.unmount();
    act(() => jest.advanceTimersByTime(599_999));
    const second = setup();
    await second.submit(url);
    expect(requestDraft).toHaveBeenCalledTimes(1);
    expect(second.result.current.modalController.form.companyName).toBe("Acme");
    expect(queryClient.getQueryData(queryKeys.applications.aiDraft(url))).toEqual(draft);
    second.unmount();
  });

  it("fetches at exactly ten minutes and cache hits do not extend freshness", async () => {
    const { submit } = setup();
    await submit();
    act(() => jest.advanceTimersByTime(300_000));
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(1);
    act(() => jest.advanceTimersByTime(299_999));
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(1);
    act(() => jest.advanceTimersByTime(1));
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(2);
  });

  it("keeps different URLs independent", async () => {
    const { submit } = setup();
    await submit();
    await submit("https://example.com/jobs/456");
    expect(requestDraft).toHaveBeenCalledTimes(2);
  });

  it("does not cache failures or automatically retry requests", async () => {
    requestDraft.mockRejectedValueOnce(new Error("Draft unavailable"));
    const { submit, result } = setup();
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(1);
    expect(result.current.flow.createWithAiError).toBeTruthy();
    expect(queryClient.getQueryData(queryKeys.applications.aiDraft(url))).toBeUndefined();
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(2);
    expect(result.current.modalController.form.companyName).toBe("Acme");
  });

  it("does not apply an expired success when refreshing it fails", async () => {
    const { submit, result } = setup();
    await submit();
    // Move the clock without running GC, leaving expired data in the cache.
    jest.setSystemTime(new Date("2026-07-12T12:10:00Z"));
    requestDraft.mockRejectedValueOnce(new Error("Refresh unavailable"));
    await submit();
    expect(result.current.flow.createWithAiError).toBeTruthy();
    expect(result.current.modalController.form.companyName).toBe("");
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(3);
  });

  it("deduplicates simultaneous requests for the same normalized URL", async () => {
    let resolveDraft!: (value: ApplicationDraftResponse) => void;
    requestDraft.mockReturnValue(new Promise(resolve => { resolveDraft = resolve; }));
    const { result } = setup();
    await act(async () => {
      const first = result.current.flow.createApplicationDraftMutation.mutateAsync({ jobUrl: url });
      const second = result.current.flow.createApplicationDraftMutation.mutateAsync({ jobUrl: url });
      await Promise.resolve();
      await Promise.resolve();
      expect(requestDraft).toHaveBeenCalledTimes(1);
      resolveDraft(draft);
      expect(await Promise.all([first, second])).toEqual([draft, draft]);
    });
  });

  it("clears drafts with the existing auth/session cache reset", async () => {
    const { submit } = setup();
    await submit();
    act(() => clearAuthSessionQueries(queryClient));
    expect(queryClient.getQueryData(queryKeys.applications.aiDraft(url))).toBeUndefined();
    await submit();
    expect(requestDraft).toHaveBeenCalledTimes(2);
  });
});
