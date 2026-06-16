import Image from "next/image";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { buttonStyles, formStyles, homeStyles } from "@/lib/styles";

export const LANDING_HEADLINE = "OfferTrack keeps your job search organized";

const featureCards = [
  {
    title: "Track applications",
    description:
      "Save roles, companies, job links, locations, and status in one private workspace.",
  },
  {
    title: "Manage interview stages",
    description:
      "Keep application progress and interview details connected as each opportunity moves forward.",
  },
  {
    title: "Follow up on time",
    description:
      "See which applications and interviews may need attention before they slip out of view.",
  },
  {
    title: "Keep notes organized",
    description:
      "Store private notes alongside each opportunity so context is easy to find later.",
  },
] as const;

export const LandingContent = () => (
  <main className={homeStyles.root}>
    <div className={homeStyles.page}>
      <header className={homeStyles.header}>
        <div className={homeStyles.brand}>
          <Image
            alt="OfferTrack logo"
            className={homeStyles.logo}
            height={40}
            priority
            src="/offertrack-logo.png"
            width={40}
          />
          <span>OfferTrack</span>
        </div>
      </header>

      <section className={homeStyles.hero}>
        <p className={homeStyles.eyebrow}>Private job application tracker</p>
        <h1 className={homeStyles.title}>{LANDING_HEADLINE}</h1>
        <p className={homeStyles.description}>
          A private job application tracker for saving opportunities,
          interviews, follow-ups, and notes in one place.
        </p>
        <div className={homeStyles.actions}>
          <Link
            className={`${formStyles.inlinePrimarySoftButton} ${homeStyles.ctaLink}`}
            href="/register"
          >
            Create account
          </Link>
          <Link
            className={`${buttonStyles.secondarySoft} ${homeStyles.ctaLink}`}
            href="/login"
          >
            Sign in
          </Link>
        </div>
      </section>

      <section aria-label="OfferTrack features" className={homeStyles.features}>
        {featureCards.map((feature) => (
          <Card className={homeStyles.featureCard} key={feature.title}>
            <h2 className={homeStyles.featureTitle}>{feature.title}</h2>
            <p className={homeStyles.featureDescription}>
              {feature.description}
            </p>
          </Card>
        ))}
      </section>

      <footer className={homeStyles.footer}>
        OfferTrack. Private job application tracking for your search.
      </footer>
    </div>
  </main>
);
