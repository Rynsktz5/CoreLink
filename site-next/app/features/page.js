const cards = [
  ["Mesh Chat", "Android-first message flow with shared Bluetooth session state, quick message composition, and direct access to peer connection tools.", ["Host or join Bluetooth peers", "Text communication inside one shared session", "Gateway screen for alerts, voice, and call tools"]],
  ["Device Radar", "Device discovery focused on nearby Bluetooth interaction rather than generic device management.", ["Paired device listing", "Nearby discovery scan", "Animated radar-style visual feedback"]],
  ["Voice Lab", "A compact audio utility page for speech-to-text drafting and local voice-note operations.", ["Voice to text", "Record and play notes", "Send transcripts and notes to connected peers"]],
  ["Emergency Center", "Fast alert staging with minimal copy and strong action hierarchy for critical scenarios.", ["Medical alert", "Shelter and supply alert", "Relay support and location share"]],
  ["Call Deck", "A dedicated session screen for call workflows and future live Bluetooth audio expansion.", ["Host and join call link actions", "Session timer layout", "Focused palette separate from alert/chat surfaces"]],
  ["Android First", "The product is designed around Android devices in the field. The website supports installation and documentation, not desktop parity.", ["Phone-first spacing and install flow", "Minimal desktop dependency", "APK distribution from the website"]]
];

export const metadata = { title: "CoreLink Features" };

export default function FeaturesPage() {
  return (
    <main>
      <section className="page-hero reveal">
        <span className="eyebrow">Features</span>
        <h1>Every major CoreLink workflow in one place.</h1>
        <p>CoreLink is built around a few focused screens instead of one overloaded interface. Each page handles a specific field task clearly.</p>
        <div className="status-strip">
          <div className="status-chip"><span>Chat</span><strong>Peer messaging</strong></div>
          <div className="status-chip"><span>Radar</span><strong>Device discovery</strong></div>
          <div className="status-chip"><span>Voice</span><strong>STT + notes</strong></div>
          <div className="status-chip"><span>Emergency</span><strong>Rapid alerts</strong></div>
        </div>
      </section>

      <section className="page-grid">
        {cards.map(([title, text, items]) => (
          <article key={title} className="page-card reveal">
            <h2>{title}</h2>
            <p>{text}</p>
            <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
          </article>
        ))}
      </section>
    </main>
  );
}
