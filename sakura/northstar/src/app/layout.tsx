import type { Metadata } from "next";
import { Shippori_Mincho, Inter } from "next/font/google";
import "./globals.css";
import "./design.css";

const shipporiMincho = Shippori_Mincho({
  variable: "--font-shippori-mincho",
  weight: ["400", "500", "600"],
  subsets: ["latin"],
});

const inter = Inter({
  variable: "--font-inter",
  weight: ["400", "500", "600"],
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "The Kansei Project",
  description: "The Kansei Project",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${shipporiMincho.variable} ${inter.variable}`}>
      <body>{children}</body>
    </html>
  );
}
