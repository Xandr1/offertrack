"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import {
  BriefcaseBusiness,
  LayoutDashboard,
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
} from "lucide-react";
import { ReactNode, useEffect, useState, useSyncExternalStore } from "react";
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

const sidebarLabelClassName =
  "inline-block max-w-28 translate-x-0 overflow-hidden whitespace-nowrap opacity-100 transition-[max-width,margin,opacity,transform] duration-300 motion-reduce:transition-none";

type ShellLayoutProps = {
  children?: ReactNode;
};

const createRetainedApplicationsHrefStore = () => {
  let href = "/applications";
  const listeners = new Set<() => void>();

  return {
    getSnapshot: () => href,
    retain: (nextHref: string) => {
      if (href === nextHref) {
        return;
      }

      href = nextHref;
      listeners.forEach((listener) => listener());
    },
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
};

export const ShellLayout = ({ children }: ShellLayoutProps) => {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [retainedApplicationsHrefStore] = useState(
    createRetainedApplicationsHrefStore,
  );
  const retainedApplicationsHref = useSyncExternalStore(
    retainedApplicationsHrefStore.subscribe,
    retainedApplicationsHrefStore.getSnapshot,
    retainedApplicationsHrefStore.getSnapshot,
  );
  const isSidebarExpanded = useSyncExternalStore(
    subscribeSidebarExpandedPreference,
    readSidebarExpandedPreference,
    getExpandedSidebarServerSnapshot,
  );

  const isApplicationsPath =
    pathname === "/applications" || pathname.startsWith("/applications/");
  const currentApplicationsHref = (() => {
    if (!isApplicationsPath) {
      return null;
    }
    const retainedParams = new URLSearchParams(searchParams.toString());
    retainedParams.delete("id");
    const retainedQuery = retainedParams.toString();
    return retainedQuery
      ? `/applications?${retainedQuery}`
      : "/applications";
  })();
  const applicationsHref =
    currentApplicationsHref ?? retainedApplicationsHref;

  useEffect(() => {
    if (currentApplicationsHref) {
      retainedApplicationsHrefStore.retain(currentApplicationsHref);
    }
  }, [currentApplicationsHref, retainedApplicationsHrefStore]);

  const toggleSidebar = () => {
    storeSidebarExpandedPreference(!isSidebarExpanded);
  };

  return (
    <main
      className={pageStyles.appMain}
      data-protected-route={pathname}
      data-sidebar-state={isSidebarExpanded ? "expanded" : "collapsed"}
      data-testid="protected-page-shell"
    >
      <div
        data-sidebar-grid
        className={`${shellStyles.grid} ${isSidebarExpanded
            ? "md:grid-cols-[190px_minmax(0,1fr)]"
            : "md:grid-cols-[66px_minmax(0,1fr)]"
          }`}
      >
        <aside className={shellStyles.sidebar}>
          <div
            data-sidebar-header
            className="mb-6 min-h-10 md:relative md:h-20"
          >
            <div
              data-sidebar-brand
              className={shellStyles.brand}
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
                data-sidebar-brand-label
                data-sidebar-label
                className={sidebarLabelClassName}
              >
                OfferTrack
              </span>
            </div>

            <div
              className={`absolute -left-3 -right-3 top-11 hidden h-10 items-center justify-end border-y pr-[17px] transition-colors duration-300 motion-reduce:transition-none md:flex ${
                isSidebarExpanded
                  ? "border-zinc-200"
                  : "border-transparent"
              }`}
              data-sidebar-toggle-row
            >
              <button
                aria-expanded={isSidebarExpanded}
                aria-label={
                  isSidebarExpanded ? "Collapse sidebar" : "Expand sidebar"
                }
                className="inline-flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-lg text-zinc-500 outline-none transition-colors duration-300 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-violet-500 motion-reduce:transition-none"
                data-sidebar-toggle
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
            </div>
          </div>

          <nav aria-label="Primary navigation" className={shellStyles.nav}>
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive =
                item.href === pathname ||
                pathname.startsWith(`${item.href}/`);

              return (
                <Link
                  aria-current={isActive ? "page" : undefined}
                  aria-label={item.label}
                  data-sidebar-nav-link
                  className={`${isActive ? shellStyles.navLinkActive : shellStyles.navLink} group relative`}
                  href={
                    item.href === "/applications"
                      ? applicationsHref
                      : item.href
                  }
                  key={item.href}
                >
                  <Icon aria-hidden className="h-[18px] w-[18px] shrink-0" />
                  <span
                    data-sidebar-label
                    data-sidebar-nav-label
                    className={sidebarLabelClassName}
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
