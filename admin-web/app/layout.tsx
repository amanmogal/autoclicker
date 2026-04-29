import type { Metadata } from "next";
import "./globals.css";

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
      <body>{children}</body>
    </html>
  );
}
