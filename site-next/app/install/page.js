import { downloadHref } from "../../components/siteData";

export const metadata = { title: "Install CoreLink" };

export default function InstallPage() {
  return (
    <main>
      <section className="page-hero reveal">
        <span className="eyebrow">Install</span>
        <h1>Download and install CoreLink on Android.</h1>
        <p>CoreLink is distributed as an Android APK. The browser downloads it, then Android handles the install confirmation.</p>
        <div className="inline-links">
          <a className="primary-btn" href={downloadHref} download>Download APK</a>
        </div>
      </section>

      <section className="install-steps">
        <article className="step-card reveal"><span className="timeline-step">1</span><h3>Download</h3><p>Tap the APK button on your Android phone.</p></article>
        <article className="step-card reveal"><span className="timeline-step">2</span><h3>Approve</h3><p>Allow installation from the source Android asks about.</p></article>
        <article className="step-card reveal"><span className="timeline-step">3</span><h3>Open</h3><p>Launch CoreLink and accept Bluetooth, microphone, and optional location requests when needed.</p></article>
      </section>

      <section className="page-grid">
        <article className="page-card reveal">
          <h2>Current Build</h2>
          <ul>
            <li>Package: Android APK</li>
            <li>Target use: phone install</li>
            <li>Website path: <code>public/downloads/corelink-android.apk</code></li>
          </ul>
        </article>
        <article className="page-card reveal">
          <h2>Important Note</h2>
          <ul>
            <li>Websites cannot silently install Android apps</li>
            <li>The site can start the download only</li>
            <li>Android handles the final install prompt</li>
          </ul>
        </article>
      </section>
    </main>
  );
}
