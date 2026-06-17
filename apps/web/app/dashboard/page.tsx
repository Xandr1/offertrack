"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getDashboardSummary, getCurrentUser } from "@/lib/api";
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

  const summary = summaryQuery.data;

  return (
    <ShellLayout activeRoute="/dashboard">
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
          <section className="flex flex-col gap-4 xl:flex-row xl:items-start">
            <div className="contents xl:flex xl:flex-1 xl:flex-col xl:gap-4">
              <div className="order-1 xl:order-none">
                <DashboardActionModule
                  count={summary?.draftsToApplyCount ?? 0}
                  helperText="Applications still waiting to be applied."
                  isLoading={summaryQuery.isPending}
                  items={summary?.draftsToApply ?? []}
                  kind="applications"
                  title="Drafts to apply"
                  viewAllHref="/applications?stage=initial"
                />
              </div>
              <div className="order-3 xl:order-none">
                <DashboardActionModule
                  count={summary?.upcomingInterviewsCount ?? 0}
                  helperText={
                    summary
                      ? `Scheduled in the next ${summary.upcomingInterviewDays} days.`
                      : "Scheduled interviews coming up soon."
                  }
                  isLoading={summaryQuery.isPending}
                  items={summary?.upcomingInterviews ?? []}
                  kind="interviews"
                  title="Upcoming interviews"
                  viewAllHref="/applications?stage=interviewing"
                />
              </div>
            </div>

            <div className="contents xl:flex xl:flex-1 xl:flex-col xl:gap-4">
              <div className="order-2 xl:order-none">
                <DashboardActionModule
                  count={summary?.applicationsToFollowUpCount ?? 0}
                  helperText={
                    summary
                      ? `Applied at least ${summary.followUpAfterApplyingDays} days ago.`
                      : "Applied applications that may need a follow-up."
                  }
                  isLoading={summaryQuery.isPending}
                  items={summary?.applicationsToFollowUp ?? []}
                  kind="applications"
                  title="Applications to follow up"
                  viewAllHref="/applications?stage=applied"
                />
              </div>
              <div className="order-4 xl:order-none">
                <DashboardActionModule
                  count={summary?.interviewsToFollowUpCount ?? 0}
                  helperText={
                    summary
                      ? `Completed at least ${summary.followUpAfterInterviewDays} days ago.`
                      : "Completed interviews waiting on next steps."
                  }
                  isLoading={summaryQuery.isPending}
                  items={summary?.interviewsToFollowUp ?? []}
                  kind="interviews"
                  title="Interviews to follow up"
                  viewAllHref="/applications?stage=interviewing"
                />
              </div>
            </div>
          </section>
        )}
      </div>
    </ShellLayout>
  );
}
