"use client";

import Image from "next/image";
import Link from "next/link";
import {
  BriefcaseBusiness,
  LayoutDashboard,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
} from "lucide-react";
import { ReactNode, useSyncExternalStore } from "react";
import { pageStyles, shellStyles } from "@/lib/styles";
import {
  getExpandedSidebarServerSnapshot,
  readSidebarExpandedPreference,
  storeSidebarExpandedPreference,
  subscribeSidebarExpandedPreference,
} from "./sidebar-preference";

const navItems = [
  { href: "/dashboard", icon: LayoutDashboard, label: "Dashboard" },
  { href: "/applications", icon: BriefcaseBusiness, label: "Applications" },
  { href: "/settings", icon: Settings, label: "Settings" },
] as const;

type ShellRoute = (typeof navItems)[number]["href"];

type ShellLayoutProps = {
  activeRoute: ShellRoute;
  children?: ReactNode;
};

export const ShellLayout = ({ activeRoute, children }: ShellLayoutProps) => {
  const isSidebarExpanded = useSyncExternalStore(
    subscribeSidebarExpandedPreference,
    readSidebarExpandedPreference,
    getExpandedSidebarServerSnapshot,
  );

  const toggleSidebar = () => {
    storeSidebarExpandedPreference(!isSidebarExpanded);
  };

  return (
    <main
      className={pageStyles.appMain}
      data-protected-route={activeRoute}
      data-sidebar-state={isSidebarExpanded ? "expanded" : "collapsed"}
      data-testid="protected-page-shell"
    >
      <div
        data-sidebar-grid
        className={`${shellStyles.grid} ${
          isSidebarExpanded
            ? "md:grid-cols-[190px_minmax(0,1fr)]"
            : "md:grid-cols-[66px_minmax(0,1fr)]"
        }`}
      >
        <aside className={shellStyles.sidebar}>
          <div
            data-sidebar-brand
            className={`${shellStyles.brand} ${
              isSidebarExpanded ? "md:justify-start" : "md:justify-center"
            }`}
          >
            <Image
              alt="OfferTrack logo"
              className={shellStyles.logo}
              height={32}
              priority
              src="/offertrack-logo.png"
              width={32}
            />
            <span
              data-sidebar-label
              className={`overflow-hidden whitespace-nowrap transition-[max-width,opacity] duration-300 motion-reduce:transition-none ${
                isSidebarExpanded
                  ? "md:max-w-28 md:opacity-100"
                  : "md:max-w-0 md:opacity-0"
              }`}
            >
              OfferTrack
            </span>
          </div>

          <button
            aria-expanded={isSidebarExpanded}
            aria-label={
              isSidebarExpanded ? "Collapse sidebar" : "Expand sidebar"
            }
            className="absolute -right-5 top-12 z-20 hidden h-10 w-10 items-center justify-center rounded-full border border-zinc-200 bg-white text-zinc-600 shadow-sm outline-none transition hover:bg-zinc-50 hover:text-zinc-950 focus-visible:ring-2 focus-visible:ring-violet-500 focus-visible:ring-offset-2 md:inline-flex"
            onClick={toggleSidebar}
            type="button"
          >
            <PanelLeftClose
              aria-hidden
              className={isSidebarExpanded ? "h-4 w-4" : "hidden h-4 w-4"}
              data-sidebar-collapse-icon
            />
            <PanelLeftOpen
              aria-hidden
              className={isSidebarExpanded ? "hidden h-4 w-4" : "h-4 w-4"}
              data-sidebar-expand-icon
            />
          </button>

          <nav aria-label="Primary navigation" className={shellStyles.nav}>
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = item.href === activeRoute;

              return (
                <Link
                  aria-current={isActive ? "page" : undefined}
                  aria-label={item.label}
                  data-sidebar-nav-link
                  className={`${isActive ? shellStyles.navLinkActive : shellStyles.navLink} ${
                    isSidebarExpanded ? "" : "md:justify-center md:px-2"
                  } group relative`}
                  href={item.href}
                  key={item.href}
                >
                  <Icon aria-hidden className="h-[18px] w-[18px] shrink-0" />
                  <span
                    data-sidebar-label
                    className={`overflow-hidden whitespace-nowrap transition-[max-width,opacity] duration-300 motion-reduce:transition-none ${
                      isSidebarExpanded
                        ? "md:max-w-28 md:opacity-100"
                        : "md:max-w-0 md:opacity-0"
                    }`}
                  >
                    {item.label}
                  </span>
                  {!isSidebarExpanded && (
                    <span
                      className="pointer-events-none absolute left-full top-1/2 z-30 ml-3 hidden -translate-y-1/2 whitespace-nowrap rounded-md bg-zinc-950 px-2 py-1 text-xs font-medium text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100 motion-reduce:transition-none md:block"
                      role="tooltip"
                    >
                      {item.label}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>
        </aside>

        <section className={shellStyles.panel}>{children}</section>
      </div>
    </main>
  );
};
