const cards = [
  ["Install Basics", ["Download the APK on Android", "Allow install from browser or file manager", "Open CoreLink after install completes"]],
  ["Required Permissions", ["Bluetooth for peer discovery and session connection", "Microphone for speech and voice notes", "Location only when location sharing is used"]],
  ["Connection Flow", ["Open chat", "Host a room or join a paired peer", "Use the shared session across other feature pages"]],
  ["Device Discovery", ["Radar page lists paired devices first", "Nearby scan updates the radar list", "Choose a paired peer to connect"]],
  ["Voice Workflow", ["Use voice-to-text to draft speech", "Record voice note locally", "Send transcript or note through the connected session"]],
  ["Troubleshooting", ["Turn Bluetooth on before connecting", "Pair devices in Android settings first", "Grant permissions when the app asks"]]
];

export const metadata = { title: "CoreLink Docs" };

export default function DocsPage() {
  return (
    <main>
      <section className="page-hero reveal">
        <span className="eyebrow">Docs</span>
        <h1>Setup, connect, and operate CoreLink.</h1>
        <p>Short documentation for Android users, testers, and contributors who need the app working quickly.</p>
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
