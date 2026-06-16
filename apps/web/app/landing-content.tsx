import Image from "next/image";
import Link from "next/link";
import {
  BriefcaseBusiness,
  CalendarCheck,
  Clock,
  StickyNote,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { buttonStyles, homeStyles } from "@/lib/styles";

export const LANDING_HEADLINE = "OfferTrack keeps your job search organized";

export const LANDING_FEATURES = [
  {
    Icon: BriefcaseBusiness,
    title: "Track applications",
    description:
      "Save roles, companies, job links, locations, and status in one private workspace.",
  },
  {
    Icon: CalendarCheck,
    title: "Manage interview stages",
    description:
      "Keep application progress and interview details connected as each opportunity moves forward.",
  },
  {
    Icon: Clock,
    title: "Follow up on time",
    description:
      "See which applications and interviews may need attention before they slip out of view.",
  },
  {
    Icon: StickyNote,
    title: "Keep notes organized",
    description:
      "Store private notes alongside each opportunity so context is easy to find later.",
  },
] as const;

export const HOW_IT_WORKS_STEPS = [
  {
    title: "Save an opportunity",
    description:
      "Add the company, role, job link, location, and any notes you want to keep.",
  },
  {
    title: "Track progress",
    description:
      "Move applications through stages and keep interview details connected to each role.",
  },
  {
    title: "Follow up on time",
    description:
      "See what needs attention before promising opportunities slip out of view.",
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
        <p className={homeStyles.eyebrow}>Your job application tracker</p>
        <h1 className={homeStyles.title}>{LANDING_HEADLINE}</h1>
        <p className={homeStyles.description}>
          Your job application tracker for saving opportunities, interviews,
          follow-ups, and notes in one place.
        </p>
        <div className={homeStyles.actions}>
          <Link
            className={`${buttonStyles.primarySoft} ${homeStyles.ctaLink}`}
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

      <section
        aria-labelledby="how-it-works-heading"
        className={homeStyles.section}
      >
        <div className={homeStyles.sectionHeader}>
          <h2 className={homeStyles.sectionTitle} id="how-it-works-heading">
            How it works
          </h2>
          <p className={homeStyles.sectionIntro}>
            Keep your search moving with a simple workflow.
          </p>
        </div>
        <div className={homeStyles.stepsGrid}>
          {HOW_IT_WORKS_STEPS.map((step, index) => (
            <Card className={homeStyles.stepCard} key={step.title}>
              <div className={homeStyles.stepNumber}>{index + 1}</div>
              <h3 className={homeStyles.stepTitle}>{step.title}</h3>
              <p className={homeStyles.stepDescription}>{step.description}</p>
            </Card>
          ))}
        </div>
      </section>

      <section
        aria-labelledby="features-heading"
        className={homeStyles.section}
      >
        <div className={homeStyles.sectionHeader}>
          <h2 className={homeStyles.sectionTitle} id="features-heading">
            Keep every opportunity organized
          </h2>
          <p className={homeStyles.sectionIntro}>
            Track applications, interviews, follow-ups, and notes without
            losing context.
          </p>
        </div>
        <div className={homeStyles.features}>
          {LANDING_FEATURES.map((feature) => {
            const FeatureIcon = feature.Icon;

            return (
              <Card className={homeStyles.featureCard} key={feature.title}>
                <div className={homeStyles.featureIcon}>
                  <FeatureIcon aria-hidden="true" size={20} strokeWidth={2} />
                </div>
                <h2 className={homeStyles.featureTitle}>{feature.title}</h2>
                <p className={homeStyles.featureDescription}>
                  {feature.description}
                </p>
              </Card>
            );
          })}
        </div>
      </section>

      <footer className={homeStyles.footer}>
        OfferTrack. Your job application tracking for your search.
      </footer>
    </div>
  </main>
);
