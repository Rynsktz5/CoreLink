const roadmap = [
  ["Live Bluetooth audio", "Move from call workflow UI to true real-time audio transport."],
  ["Multi-hop relay", "Forward alerts and messages across more than one nearby node."],
  ["Group sessions", "Expand from pair-based communication toward wider local coordination."],
  ["Message persistence", "Store and recover local communication state more reliably."],
  ["Operational logs", "Provide mission-friendly event history and export options."],
  ["Security hardening", "Strengthen session handling and transport safeguards over time."],
];

export const metadata = { title: "CoreLink Roadmap" };

export default function RoadmapPage() {
  return (
    <main>
      <section className="page-hero reveal">
        <span className="eyebrow">Roadmap</span>
        <h1>What we can build next.</h1>
        <p>CoreLink already has a strong Android-first prototype. The roadmap focuses on turning it into a more complete field communication platform.</p>
      </section>

      <section className="roadmap-grid">
        {roadmap.map(([title, text]) => (
          <article key={title} className="roadmap-card reveal">
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </section>
    </main>
  );
}
