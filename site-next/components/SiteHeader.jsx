import Link from "next/link";
import { downloadHref, navItems } from "./siteData";

export default function SiteHeader() {
  return (
    <header className="topbar">
      <Link className="brand" href="/">
        <img src="/assets/corelink-icon.png" alt="CoreLink icon" />
        <div>
          <span>CoreLink</span>
          <small>Android mesh comms</small>
        </div>
      </Link>

      <nav className="topnav">
        {navItems.map((item) => (
          <Link key={item.href} href={item.href}>
            {item.label}
          </Link>
        ))}
      </nav>

      <a className="nav-cta" href={downloadHref} download>
        Download APK
      </a>
    </header>
  );
}
