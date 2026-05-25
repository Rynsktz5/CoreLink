import { Inter, Space_Grotesk } from "next/font/google";
import "./globals.css";
import SiteHeader from "../components/SiteHeader";
import SiteFooter from "../components/SiteFooter";
import FloatingInstall from "../components/FloatingInstall";
import AnimatedSiteEffects from "../components/AnimatedSiteEffects";

const inter = Inter({ subsets: ["latin"], variable: "--font-body" });
const spaceGrotesk = Space_Grotesk({ subsets: ["latin"], variable: "--font-display" });

export const metadata = {
  title: "CoreLink | Android Mesh Communication",
  description:
    "CoreLink is an Android-first mesh communication app built for resilient Bluetooth-based messaging, alerts, voice tools, and emergency coordination.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body className={`${inter.variable} ${spaceGrotesk.variable}`}>
        <div className="site-shell">
          <SiteHeader />
          {children}
          <SiteFooter />
        </div>
        <FloatingInstall />
        <AnimatedSiteEffects />
      </body>
    </html>
  );
}
