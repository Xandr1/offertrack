import Image from "next/image";
import Link from "next/link";
import { ReactNode } from "react";
import { pageStyles, shellStyles } from "@/lib/styles";

const navItems = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/applications", label: "Applications" },
] as const;

type ShellRoute = (typeof navItems)[number]["href"];

type ShellLayoutProps = {
  activeRoute: ShellRoute;
  children: ReactNode;
  sidebarFooter?: ReactNode;
};

export const ShellLayout = ({
  activeRoute,
  children,
  sidebarFooter,
}: ShellLayoutProps) => (
  <main className={pageStyles.appMain}>
    <div className={shellStyles.grid}>
      <aside
        className={[
          shellStyles.sidebar,
          sidebarFooter ? shellStyles.sidebarWithFooter : null,
        ]
          .filter(Boolean)
          .join(" ")}
      >
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
        <nav
          className={[
            shellStyles.nav,
            sidebarFooter ? shellStyles.navWithFooter : null,
          ]
            .filter(Boolean)
            .join(" ")}
        >
          {navItems.map((item) => (
            <Link
              aria-current={item.href === activeRoute ? "page" : undefined}
              className={
                item.href === activeRoute
                  ? shellStyles.navLinkActive
                  : shellStyles.navLink
              }
              href={item.href}
              key={item.href}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        {sidebarFooter && (
          <div className={shellStyles.footer}>{sidebarFooter}</div>
        )}
      </aside>

      <section className={shellStyles.panel}>{children}</section>
    </div>
  </main>
);
