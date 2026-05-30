"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getDashboardSummary, getCurrentUser, logout } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import {
  getRequestErrorMessage,
  isAuthError,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import {
  buttonStyles,
  cardStyles,
  formStyles,
  layoutStyles,
  pageStyles,
  shellStyles,
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
        <div className={cardStyles.auth}>
          <h1 className={textStyles.pageTitle}>Dashboard unavailable</h1>
          <div className="mt-4">
            <div className={formStyles.error}>
              {getRequestErrorMessage(userQuery.error)}
            </div>
          </div>
          <button
            className={buttonStyles.secondaryWithTopMargin}
            onClick={() => userQuery.refetch()}
            type="button"
          >
            Retry
          </button>
        </div>
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
    <main className={pageStyles.appMain}>
      <div className={shellStyles.grid}>
        <aside className={shellStyles.sidebar}>
          <div className={shellStyles.brand}>
            <Image
              alt="OfferTrack logo"
              className={shellStyles.logo}
              height={32}
              priority
              src="/offertrack-logo.png"
              width={32}
            />
            <span>OfferTrack</span>
          </div>
          <nav className={shellStyles.nav}>
            <Link
              aria-current="page"
              className={shellStyles.navLinkActive}
              href="/dashboard"
            >
              Dashboard
            </Link>
            <Link className={shellStyles.navLink} href="/applications">
              Applications
            </Link>
          </nav>
        </aside>

        <section className={shellStyles.panel}>
          <div className={layoutStyles.container}>
            <header className={layoutStyles.header}>
              <div>
                <h1 className={textStyles.pageTitle}>Dashboard</h1>
                <p className={textStyles.description}>Signed in as {user.email}</p>
              </div>

              <div className={layoutStyles.actionRow}>
                <button onClick={handleLogout} className={buttonStyles.secondary}>
                  Logout
                </button>
              </div>
            </header>

            <section className={layoutStyles.metricsGrid}>
              {metricCards.map((card) => (
                <div key={card.label} className={cardStyles.default}>
                  <div className={textStyles.label}>{card.label}</div>
                  <div className={textStyles.statValue}>
                    {summaryQuery.isPending ? "..." : card.value}
                  </div>
                </div>
              ))}
            </section>

            {summaryQuery.error && (
              <section className={layoutStyles.section}>
                <div className={cardStyles.default}>
                  <h2 className={textStyles.sectionTitle}>Summary unavailable</h2>
                  <div className="mt-4">
                    <div className={formStyles.error}>
                      {getRequestErrorMessage(summaryQuery.error)}
                    </div>
                  </div>
                  <button
                    className={buttonStyles.secondaryWithTopMargin}
                    onClick={() => summaryQuery.refetch()}
                    type="button"
                  >
                    Retry
                  </button>
                </div>
              </section>
            )}

            <section className={layoutStyles.section}>
              <div className={cardStyles.default}>
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
              </div>
            </section>
          </div>
        </section>
      </div>
    </main>
  );
}
