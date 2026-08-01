import type { Metadata } from "next";
import { Roboto } from "next/font/google";
import { SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT } from "@/components/layout/sidebar-preference";
import "./globals.css";
import { ProtectedAppBoundary } from "./protected-app-boundary";
import { Providers } from "./providers";

const roboto = Roboto({
  variable: "--font-roboto",
  weight: ["400", "500", "700"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "OfferTrack",
  description: "Track job applications and interview progress.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${roboto.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT,
          }}
        />
      </head>
      <body className="min-h-full flex flex-col">
        <Providers>
          <ProtectedAppBoundary>{children}</ProtectedAppBoundary>
        </Providers>
      </body>
    </html>
  );
}
