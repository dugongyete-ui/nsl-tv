import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "NSL TV — Unduh Aplikasi Android",
  description:
    "NSL TV: aplikasi IPTV Android ringan dan halus. 200+ saluran live, jadwal pertandingan, DRM Widevine, Picture-in-Picture. Unduh APK rilis resmi.",
  keywords: ["NSL TV", "IPTV", "APK", "live TV", "streaming"],
  icons: {
    icon: "/nsl/icon-192.png",
    apple: "/nsl/icon-192.png",
  },
  openGraph: {
    title: "NSL TV — Unduh Aplikasi Android",
    description: "Aplikasi IPTV Android ringan: 200+ saluran live, sports & event.",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#070B1E",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="id" suppressHydrationWarning>
      <body className="antialiased bg-background text-foreground">{children}</body>
    </html>
  );
}
