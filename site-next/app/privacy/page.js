const cards = [
  ["Bluetooth", ["Used for peer discovery and peer connection", "Not used for cloud syncing", "Only relevant when Bluetooth features are used"]],
  ["Microphone", ["Used for speech-to-text and voice notes", "Not needed for text-only use", "Requested only for voice features"]],
  ["Location", ["Used for optional location sharing in emergency workflows", "Not required for basic chat", "Requested only when the feature is used"]],
  ["Website", ["Provides download and documentation", "Does not replace the Android app", "Desktop browsing is informational only"]],
];

export const metadata = { title: "CoreLink Privacy" };

export default function PrivacyPage() {
  return (
    <main>
      <section className="page-hero reveal">
        <span className="eyebrow">Privacy</span>
        <h1>Simple privacy expectations for CoreLink.</h1>
        <p>CoreLink is focused on Android device-to-device communication. The website mainly distributes builds and explains how the app works.</p>
      </section>

      <section className="page-grid">
        {cards.map(([title, items]) => (
          <article key={title} className="page-card reveal">
            <h2>{title}</h2>
            <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
          </article>
        ))}
      </section>
    </main>
  );
}
