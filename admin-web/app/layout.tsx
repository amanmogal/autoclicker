import type { Metadata } from "next";
import { IBM_Plex_Mono, Space_Grotesk } from "next/font/google";
import "./globals.css";

const headlineFont = Space_Grotesk({
  subsets: ["latin"],
  variable: "--font-headline"
});

const monoFont = IBM_Plex_Mono({
  subsets: ["latin"],
  variable: "--font-mono",
  weight: ["400", "500"]
});

export const metadata: Metadata = {
  title: "Bhada Approval Admin",
  description: "Approve or revoke driver access backed by Supabase Auth."
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={`${headlineFont.variable} ${monoFont.variable}`}>{children}</body>
    </html>
  );
}
