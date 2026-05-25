import Link from "next/link";
import { downloadHref } from "./siteData";

export default function SiteFooter() {
  return (
    <footer className="footer reveal">
      <div>
        <strong>CoreLink</strong>
        <p>Android-first mesh communication for low-connectivity and emergency environments.</p>
      </div>
      <div className="footer-actions">
        <Link className="secondary-btn" href="/features">
          Explore Features
        </Link>
        <a className="primary-btn" href={downloadHref} download>
          Get the APK
        </a>
      </div>
    </footer>
  );
}
