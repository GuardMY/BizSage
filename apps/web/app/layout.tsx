import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BizSage V1",
  description: "Industry diagnosis Agent MVP"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
