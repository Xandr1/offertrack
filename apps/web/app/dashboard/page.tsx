"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getDashboardApplicationsToFollowUp,
  getDashboardInterviewsToFollowUp,
  getDashboardSummary,
  getDashboardUpcomingInterviews,
  markApplicationFollowedUp,
  markInterviewFollowedUp,
} from "@/lib/api";
import type { DashboardSummary } from "@/lib/api";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { ShellLayout } from "@/components/layout/shell-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { queryKeys } from "@/lib/query-keys";
import { useRedirectToLoginOnProtectedError } from "@/lib/auth/use-redirect-to-login-on-protected-error";
import {
  getRequestErrorMessage,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { formStyles, layoutStyles, textStyles } from "@/lib/styles";
import { invalidateAfterDashboardFollowUp } from "../applications/services/applications-cache-service";
import { DashboardActionModule } from "./components/dashboard-action-module";

export const FOLLOW_UP_UNDO_TIMEOUT_MS = 3000;

type PendingFollowUp = {
  applicationId: string;
  interviewId?: string;
};

const removeApplicationItem = (
  summary: DashboardSummary,
  applicationId: string,
): DashboardSummary => {
  const page = summary.applicationsToFollowUp;
  if (!page.items.some((item) => item.applicationId === applicationId)) return summary;
  const totalCount = Math.max(0, page.totalCount - 1);
  const nextOffset = Math.max(0, page.nextOffset - 1);
  return {
    ...summary,
    needsAttention: Math.max(0, summary.needsAttention - 1),
    applicationsToFollowUp: {
      ...page,
      items: page.items.filter((item) => item.applicationId !== applicationId),
      totalCount,
      nextOffset,
      hasMore: nextOffset < totalCount,
    },
  };
};

const removeInterviewItem = (
  summary: DashboardSummary,
  interviewId: string,
): DashboardSummary => {
  const page = summary.interviewsToFollowUp;
  if (!page.items.some((item) => item.interviewId === interviewId)) return summary;
  const totalCount = Math.max(0, page.totalCount - 1);
  const nextOffset = Math.max(0, page.nextOffset - 1);
  return {
    ...summary,
    needsAttention: Math.max(0, summary.needsAttention - 1),
    interviewsToFollowUp: {
      ...page,
      items: page.items.filter((item) => item.interviewId !== interviewId),
      totalCount,
      nextOffset,
      hasMore: nextOffset < totalCount,
    },
  };
};

function DashboardPageContent() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const timersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const [pendingApplicationIds, setPendingApplicationIds] = useState(new Set<string>());
  const [pendingInterviewIds, setPendingInterviewIds] = useState(new Set<string>());
  const [applicationError, setApplicationError] = useState<string | null>(null);
  const [upcomingError, setUpcomingError] = useState<string | null>(null);
  const [interviewError, setInterviewError] = useState<string | null>(null);

  const summaryQuery = useQuery({
    queryKey: queryKeys.dashboardSummary,
    queryFn: getDashboardSummary,
    retry: false,
  });
  const isRedirectingToLogin = useRedirectToLoginOnProtectedError(
    summaryQuery.error,
  );

  const handleDashboardError = useCallback(
    async (error: unknown, setError: (message: string) => void) => {
      if (await redirectToLoginIfProtectedRoute(error, router, queryClient)) {
        return;
      }

      setError(getRequestErrorMessage(error));
    },
    [queryClient, router],
  );

  useEffect(() => () => {
    for (const timer of timersRef.current.values()) clearTimeout(timer);
    timersRef.current.clear();
  }, []);

  const applicationMutation = useMutation({
    mutationFn: ({ applicationId }: PendingFollowUp) => markApplicationFollowedUp(applicationId),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<DashboardSummary>(queryKeys.dashboardSummary, (current) =>
        current ? removeApplicationItem(current, variables.applicationId) : current,
      );
      invalidateAfterDashboardFollowUp(queryClient, {
        applicationId: variables.applicationId,
        kind: "application",
      });
    },
    onError: (error) => {
      void handleDashboardError(error, setApplicationError);
    },
    onSettled: (_data, _error, variables) => {
      setPendingApplicationIds((current) => {
        const next = new Set(current); next.delete(variables.applicationId); return next;
      });
    },
  });

  const interviewMutation = useMutation({
    mutationFn: ({ applicationId, interviewId }: PendingFollowUp) =>
      markInterviewFollowedUp(applicationId, interviewId!),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<DashboardSummary>(queryKeys.dashboardSummary, (current) =>
        current ? removeInterviewItem(current, variables.interviewId!) : current,
      );
      invalidateAfterDashboardFollowUp(queryClient, {
        applicationId: variables.applicationId,
        kind: "interview",
      });
    },
    onError: (error) => {
      void handleDashboardError(error, setInterviewError);
    },
    onSettled: (_data, _error, variables) => {
      setPendingInterviewIds((current) => {
        const next = new Set(current); next.delete(variables.interviewId!); return next;
      });
    },
  });

  const applicationLoadMore = useMutation({
    mutationFn: getDashboardApplicationsToFollowUp,
    onSuccess: (page) => queryClient.setQueryData<DashboardSummary>(queryKeys.dashboardSummary, (current) => current && ({
      ...current,
      applicationsToFollowUp: { ...page, items: [...current.applicationsToFollowUp.items, ...page.items] },
    })),
    onError: (error) => {
      void handleDashboardError(error, setApplicationError);
    },
  });
  const upcomingLoadMore = useMutation({
    mutationFn: getDashboardUpcomingInterviews,
    onSuccess: (page) => queryClient.setQueryData<DashboardSummary>(queryKeys.dashboardSummary, (current) => current && ({
      ...current,
      upcomingInterviews: { ...page, items: [...current.upcomingInterviews.items, ...page.items] },
    })),
    onError: (error) => {
      void handleDashboardError(error, setUpcomingError);
    },
  });
  const interviewLoadMore = useMutation({
    mutationFn: getDashboardInterviewsToFollowUp,
    onSuccess: (page) => queryClient.setQueryData<DashboardSummary>(queryKeys.dashboardSummary, (current) => current && ({
      ...current,
      interviewsToFollowUp: { ...page, items: [...current.interviewsToFollowUp.items, ...page.items] },
    })),
    onError: (error) => {
      void handleDashboardError(error, setInterviewError);
    },
  });

  const markFollowedUp = (applicationId: string, interviewId?: string) => {
    const id = interviewId ?? applicationId;
    if (timersRef.current.has(id)) return;
    if (interviewId) {
      setInterviewError(null);
      setPendingInterviewIds((current) => new Set(current).add(interviewId));
    } else {
      setApplicationError(null);
      setPendingApplicationIds((current) => new Set(current).add(applicationId));
    }
    const timer = setTimeout(() => {
      timersRef.current.delete(id);
      if (interviewId) interviewMutation.mutate({ applicationId, interviewId });
      else applicationMutation.mutate({ applicationId });
    }, FOLLOW_UP_UNDO_TIMEOUT_MS);
    timersRef.current.set(id, timer);
  };

  const undo = (id: string) => {
    const timer = timersRef.current.get(id);
    if (!timer) return;
    clearTimeout(timer);
    timersRef.current.delete(id);
    setPendingApplicationIds((current) => { const next = new Set(current); next.delete(id); return next; });
    setPendingInterviewIds((current) => { const next = new Set(current); next.delete(id); return next; });
  };

  const summary = summaryQuery.data;
  const now = new Date();

  if (isRedirectingToLogin) {
    return null;
  }

  return (
    <ShellLayout activeRoute="/dashboard">
      <div className={layoutStyles.container}>
        {summaryQuery.error && <Card><h2 className={textStyles.sectionTitle}>Summary unavailable</h2><div className={`mt-4 ${formStyles.error}`}>{getRequestErrorMessage(summaryQuery.error)}</div><Button className="mt-4" onClick={() => summaryQuery.refetch()} variant="secondary">Retry</Button></Card>}
        {!summaryQuery.error && <section className="grid gap-4 xl:grid-cols-3">
          <DashboardActionModule
            count={summary?.applicationsToFollowUp.totalCount ?? 0}
            errorMessage={applicationError}
            followUpAfterApplyingDays={summary?.followUpAfterApplyingDays ?? 0}
            hasMore={summary?.applicationsToFollowUp.hasMore ?? false}
            helperText={summary ? `Applied at least ${summary.followUpAfterApplyingDays} days ago.` : "Applications that may need a follow-up."}
            isLoading={summaryQuery.isPending}
            isLoadingMore={applicationLoadMore.isPending}
            items={summary?.applicationsToFollowUp.items ?? []}
            kind="applications"
            now={now}
            pendingIds={pendingApplicationIds}
            title="Applications to follow up"
            onLoadMore={() => summary && applicationLoadMore.mutate(summary.applicationsToFollowUp.nextOffset)}
            onMarkFollowedUp={markFollowedUp}
            onUndo={undo}
          />
          <DashboardActionModule
            count={summary?.upcomingInterviews.totalCount ?? 0}
            errorMessage={upcomingError}
            hasMore={summary?.upcomingInterviews.hasMore ?? false}
            helperText={summary ? `Scheduled in the next ${summary.upcomingInterviewDays} days.` : "Scheduled interviews coming up soon."}
            isLoading={summaryQuery.isPending}
            isLoadingMore={upcomingLoadMore.isPending}
            items={summary?.upcomingInterviews.items ?? []}
            kind="upcoming-interviews"
            now={now}
            pendingIds={new Set<string>()}
            title="Upcoming interviews"
            onLoadMore={() => summary && upcomingLoadMore.mutate(summary.upcomingInterviews.nextOffset)}
            onMarkFollowedUp={markFollowedUp}
            onUndo={undo}
          />
          <DashboardActionModule
            count={summary?.interviewsToFollowUp.totalCount ?? 0}
            errorMessage={interviewError}
            followUpAfterInterviewDays={summary?.followUpAfterInterviewDays ?? 0}
            hasMore={summary?.interviewsToFollowUp.hasMore ?? false}
            helperText={summary ? `Interviewed at least ${summary.followUpAfterInterviewDays} days ago.` : "Interviews waiting on an outcome."}
            isLoading={summaryQuery.isPending}
            isLoadingMore={interviewLoadMore.isPending}
            items={summary?.interviewsToFollowUp.items ?? []}
            kind="interviews-to-follow-up"
            now={now}
            pendingIds={pendingInterviewIds}
            title="Interviews to follow up"
            onLoadMore={() => summary && interviewLoadMore.mutate(summary.interviewsToFollowUp.nextOffset)}
            onMarkFollowedUp={markFollowedUp}
            onUndo={undo}
          />
        </section>}
      </div>
    </ShellLayout>
  );
}

export default function DashboardPage() {
  return (
    <ProtectedRoute
      errorTitle="Dashboard unavailable"
      loadingLabel="Loading dashboard..."
    >
      {() => <DashboardPageContent />}
    </ProtectedRoute>
  );
}
