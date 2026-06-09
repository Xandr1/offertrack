"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getDashboardSummary, getCurrentUser, logout } from "@/lib/api";
import { ShellLayout } from "@/components/layout/shell-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { queryKeys } from "@/lib/query-keys";
import {
  getRequestErrorMessage,
  isAuthError,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import {
  formStyles,
  layoutStyles,
  pageStyles,
  textStyles,
} from "@/lib/styles";
import { DashboardActionModule } from "./components/dashboard-action-module";

export default function DashboardPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const userQuery = useQuery({
    queryKey: queryKeys.authMe,
    queryFn: getCurrentUser,
    retry: false,
  });

  const summaryQuery = useQuery({
    queryKey: queryKeys.dashboardSummary,
    queryFn: getDashboardSummary,
    retry: false,
    enabled: Boolean(userQuery.data),
  });

  const shouldRedirectToLogin = isAuthError(userQuery.error);

  useEffect(() => {
    if (!userQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(userQuery.error, router);
  }, [router, userQuery.error]);

  async function handleLogout() {
    try {
      await logout();
    } finally {
      queryClient.clear();
      router.replace("/login");
    }
  }

  if (userQuery.isPending) {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>Loading dashboard...</div>
      </main>
    );
  }

  if (shouldRedirectToLogin) {
    return null;
  }

  if (userQuery.error) {
    return (
      <main className={pageStyles.centered}>
        <Card variant="auth">
          <h1 className={textStyles.pageTitle}>Dashboard unavailable</h1>
          <div className="mt-4">
            <div className={formStyles.error}>
              {getRequestErrorMessage(userQuery.error)}
            </div>
          </div>
          <Button
            className="mt-4"
            onClick={() => userQuery.refetch()}
            variant="secondary"
          >
            Retry
          </Button>
        </Card>
      </main>
    );
  }

  if (!userQuery.data) {
    return null;
  }

  const user = userQuery.data;
  const summary = summaryQuery.data;

  return (
    <ShellLayout
      activeRoute="/dashboard"
      sidebarFooter={
        <div>
          <p className="break-all text-xs text-zinc-500">{user.email}</p>
          <Button
            className="mt-2 w-full justify-start px-0 text-zinc-700"
            onClick={handleLogout}
            variant="ghost"
          >
            Logout
          </Button>
        </div>
      }
    >
      <div className={layoutStyles.container}>
        {summaryQuery.error && (
          <section>
            <Card>
              <h2 className={textStyles.sectionTitle}>Summary unavailable</h2>
              <div className="mt-4">
                <div className={formStyles.error}>
                  {getRequestErrorMessage(summaryQuery.error)}
                </div>
              </div>
              <Button
                className="mt-4"
                onClick={() => summaryQuery.refetch()}
                variant="secondary"
              >
                Retry
              </Button>
            </Card>
          </section>
        )}

        {!summaryQuery.error && (
          <section className="grid gap-4 xl:grid-cols-2">
            <DashboardActionModule
              count={summary?.draftsToApplyCount ?? 0}
              helperText="Oldest drafts ready to move into applied."
              isLoading={summaryQuery.isPending}
              items={summary?.draftsToApply ?? []}
              kind="applications"
              title="Drafts to apply"
              viewAllHref="/applications?stage=initial"
            />
            <DashboardActionModule
              count={summary?.applicationsToFollowUpCount ?? 0}
              helperText="Applied roles that have been quiet long enough to check in."
              isLoading={summaryQuery.isPending}
              items={summary?.applicationsToFollowUp ?? []}
              kind="applications"
              title="Applications to follow up"
              viewAllHref="/applications?stage=applied"
            />
            <DashboardActionModule
              count={summary?.upcomingInterviewsCount ?? 0}
              helperText="Scheduled interviews in your upcoming window."
              isLoading={summaryQuery.isPending}
              items={summary?.upcomingInterviews ?? []}
              kind="interviews"
              title="Upcoming interviews"
              viewAllHref="/applications?stage=interviewing"
            />
            <DashboardActionModule
              count={summary?.interviewsToFollowUpCount ?? 0}
              helperText="Completed interviews waiting on next steps."
              isLoading={summaryQuery.isPending}
              items={summary?.interviewsToFollowUp ?? []}
              kind="interviews"
              title="Interviews to follow up"
              viewAllHref="/applications?stage=interviewing"
            />
          </section>
        )}
      </div>
    </ShellLayout>
  );
}
