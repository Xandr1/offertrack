"use client";

import { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { ShellLayout } from "@/components/layout/shell-layout";

type ProtectedAppBoundaryProps = {
  children: ReactNode;
};

const protectedRoutes = [
  {
    errorTitle: "Dashboard unavailable",
    loadingLabel: "Loading dashboard...",
    pathname: "/dashboard",
  },
  {
    errorTitle: "Applications unavailable",
    loadingLabel: "Loading applications...",
    pathname: "/applications",
  },
  {
    errorTitle: "Settings unavailable",
    loadingLabel: "Loading settings...",
    pathname: "/settings",
  },
] as const;

const getProtectedRoute = (pathname: string) =>
  protectedRoutes.find(
    (route) =>
      pathname === route.pathname || pathname.startsWith(`${route.pathname}/`),
  );

export const ProtectedAppBoundary = ({
  children,
}: ProtectedAppBoundaryProps) => {
  const pathname = usePathname();
  const protectedRoute = getProtectedRoute(pathname);

  if (!protectedRoute) {
    return <>{children}</>;
  }

  return (
    <ProtectedRoute
      errorTitle={protectedRoute.errorTitle}
      loadingLabel={protectedRoute.loadingLabel}
    >
      <ShellLayout>{children}</ShellLayout>
    </ProtectedRoute>
  );
};
