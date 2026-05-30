import Image from "next/image";
import { homeStyles } from "@/lib/styles";

export default function Home() {
  return (
    <div className={homeStyles.root}>
      <main className={homeStyles.main}>
        <Image
          className={homeStyles.invertOnDark}
          src="/next.svg"
          alt="Next.js logo"
          width={100}
          height={20}
          priority
        />
        <div className={homeStyles.intro}>
          <h1 className={homeStyles.title}>
            To get started, edit the page.tsx file.
          </h1>
          <p className={homeStyles.description}>
            Looking for a starting point or more instructions? Head over to{" "}
            <a
              href="https://vercel.com/templates?framework=next.js&utm_source=create-next-app&utm_medium=appdir-template-tw&utm_campaign=create-next-app"
              className={homeStyles.strongLink}
            >
              Templates
            </a>{" "}
            or the{" "}
            <a
              href="https://nextjs.org/learn?utm_source=create-next-app&utm_medium=appdir-template-tw&utm_campaign=create-next-app"
              className={homeStyles.strongLink}
            >
              Learning
            </a>{" "}
            center.
          </p>
        </div>
        <div className={homeStyles.actions}>
          <a
            className={homeStyles.primaryAction}
            href="https://vercel.com/new?utm_source=create-next-app&utm_medium=appdir-template-tw&utm_campaign=create-next-app"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Image
              className={homeStyles.invertOnDark}
              src="/vercel.svg"
              alt="Vercel logomark"
              width={16}
              height={16}
            />
            Deploy Now
          </a>
          <a
            className={homeStyles.secondaryAction}
            href="https://nextjs.org/docs?utm_source=create-next-app&utm_medium=appdir-template-tw&utm_campaign=create-next-app"
            target="_blank"
            rel="noopener noreferrer"
          >
            Documentation
          </a>
        </div>
      </main>
    </div>
  );
}
