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
  buttonStyles,
  formStyles,
  layoutStyles,
  pageStyles,
  sectionStyles,
  textStyles,
} from "@/lib/styles";
import { formatUpdatedAtRelative } from "../applications/helpers/application-date-helpers";
import { applicationStageLabels } from "../applications/helpers/application-labels";

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

  const metricCards = [
    {
      label: "Active processes",
      value: summary?.activeProcesses ?? 0,
    },
    {
      label: "Needs attention",
      value: summary?.needsAttention ?? 0,
    },
    {
      label: "Interviewing",
      value: summary?.interviewing ?? 0,
    },
    {
      label: "Offers",
      value: summary?.offers ?? 0,
    },
    {
      label: "Rejected",
      value: summary?.rejected ?? 0,
    },
  ];

  return (
    <ShellLayout activeRoute="/dashboard">
      <div className={layoutStyles.container}>
        <header className={layoutStyles.header}>
          <div>
            <h1 className={textStyles.pageTitle}>Dashboard</h1>
            <p className={textStyles.description}>Signed in as {user.email}</p>
          </div>

          <div className={layoutStyles.actionRow}>
            <Button onClick={handleLogout} variant="secondary">
              Logout
            </Button>
          </div>
        </header>

        <section className={layoutStyles.metricsGrid}>
          {metricCards.map((card) => (
            <Card key={card.label}>
              <div className={textStyles.label}>{card.label}</div>
              <div className={textStyles.statValue}>
                {summaryQuery.isPending ? "..." : card.value}
              </div>
            </Card>
          ))}
        </section>

        {summaryQuery.error && (
          <section className={layoutStyles.section}>
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

        <section className={layoutStyles.section}>
          <Card>
            <h2 className={textStyles.sectionTitle}>Recent applications</h2>
            <p className={textStyles.description}>Latest updates across your board.</p>

            {summaryQuery.isPending && (
              <p className="mt-4 text-sm text-zinc-700">Loading recent activity...</p>
            )}

            {!summaryQuery.isPending &&
              !summaryQuery.error &&
              summary &&
              summary.recentApplications.length === 0 && (
                <div className="mt-4">
                  <div className={sectionStyles.dashedEmpty}>
                    <p className={textStyles.muted}>No applications yet.</p>
                  </div>
                </div>
              )}

            {!summaryQuery.isPending &&
              !summaryQuery.error &&
              summary &&
              summary.recentApplications.length > 0 && (
                <ul className="mt-4 space-y-3">
                  {summary.recentApplications.map((application) => (
                    <li
                      key={application.id}
                      className={sectionStyles.listItem}
                    >
                      <div className={sectionStyles.splitRow}>
                        <div>
                          <p className={textStyles.strong}>{application.companyName}</p>
                          <p className={textStyles.muted}>{application.positionTitle}</p>
                        </div>
                        <div className="text-right">
                          <span className={buttonStyles.pill}>
                            {applicationStageLabels[application.stage]}
                          </span>
                          <p className={textStyles.timestamp}>
                            {formatUpdatedAtRelative(application.updatedAt)}
                          </p>
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
          </Card>
        </section>
      </div>
    </ShellLayout>
  );
}
