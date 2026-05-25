import Link from "next/link";
import { downloadHref } from "../components/siteData";

const timeline = [
  ["01", "Discover", "Open Device Radar to inspect paired devices and nearby Bluetooth nodes."],
  ["02", "Connect", "Host a Bluetooth room or join a paired peer from inside chat without leaving the Android flow."],
  ["03", "Communicate", "Use text, voice text, and voice notes through a shared Bluetooth session."],
  ["04", "Respond", "Send structured alerts and location-sharing details from the emergency center."],
];

const docsDetail = [
  ["Architecture", "Shared session model", "Chat, voice tools, emergency actions, and device discovery rely on one active Bluetooth session layer."],
  ["Permissions", "Minimal permission set", "Bluetooth, microphone, and optional location access are requested only where they matter."],
  ["Web role", "Support layer", "This site explains the product and distributes Android builds. The app remains the primary experience."],
];

const roadmap = [
  ["Live Bluetooth audio", "Move from call workflow UI to true live audio transport."],
  ["Multi-hop relay", "Forward alerts and messages across more than one nearby node."],
  ["Session hardening", "Add stronger message persistence and clearer reconnection behavior."],
  ["Operational logs", "Surface mission-friendly activity history and export options."],
];

const faq = [
  ["Does the website install the app directly?", "No. It downloads the APK and then Android handles the install prompt."],
  ["Why focus on Android?", "CoreLink is built for phone-based field communication. The web layer is mainly for distribution and documentation."],
  ["Can the site work on desktop?", "Yes for reading docs and downloading files, but the actual product is designed around Android devices."],
];

export default function HomePage() {
  return (
    <main id="top">
      <section className="hero">
        <div className="hero-copy reveal">
          <span className="eyebrow">Android first. Field ready.</span>
          <h1>Reliable communication when normal networks fail.</h1>
          <p>
            CoreLink is built for Android and focused on Bluetooth mesh messaging,
            alerts, voice tools, location sharing, and fast emergency coordination.
          </p>

          <div className="hero-actions">
            <a className="primary-btn" href={downloadHref} download>
              Download for Android
            </a>
            <Link className="secondary-btn" href="#gallery">
              See the screens
            </Link>
          </div>

          <ul className="hero-points">
            <li>Bluetooth peer discovery</li>
            <li>Emergency alert workflows</li>
            <li>Voice text and voice note tools</li>
          </ul>

          <div className="metric-row">
            <div className="metric-pill"><span>Platform</span><strong>Android</strong></div>
            <div className="metric-pill"><span>Transport</span><strong>Bluetooth</strong></div>
            <div className="metric-pill"><span>Focus</span><strong>Field use</strong></div>
          </div>

          <div className="scroll-cue">
            <span>Scroll to trace the mesh</span>
            <i></i>
          </div>
        </div>

        <div className="hero-visual reveal">
          <div className="hero-glow"></div>
          <div className="mesh-ribbon ribbon-one"></div>
          <div className="mesh-ribbon ribbon-two"></div>
          <div className="mesh-stage">
            <svg className="mesh-lines" viewBox="0 0 520 520" aria-hidden="true">
              <path className="mesh-beam" d="M260 80 L406 156 L414 316 L260 404 L106 316 L114 156 Z"></path>
              <path className="mesh-beam soft" d="M260 142 L342 186 L346 276 L260 326 L174 276 L178 186 Z"></path>
              <path className="mesh-beam soft" d="M260 80 L260 404"></path>
              <path className="mesh-beam soft" d="M114 156 L260 242 L406 156"></path>
              <path className="mesh-beam soft" d="M106 316 L260 242 L414 316"></path>
            </svg>
            <span className="mesh-node node-top"></span>
            <span className="mesh-node node-right-top"></span>
            <span className="mesh-node node-right-bottom"></span>
            <span className="mesh-node node-bottom"></span>
            <span className="mesh-node node-left-bottom"></span>
            <span className="mesh-node node-left-top"></span>
            <span className="mesh-node node-core"></span>
          </div>
          <div className="device-frame">
            <div className="device-card orbit-card card-a"><span>Status</span><strong>Peer linked</strong></div>
            <div className="device-card orbit-card card-b"><span>Alert</span><strong>Relay staged</strong></div>
            <div className="device-card orbit-card card-c"><span>Voice</span><strong>Text ready</strong></div>
            <img src="/assets/corelink-icon.png" alt="CoreLink app artwork" />
          </div>
        </div>
      </section>

      <section className="network-scene reveal">
        <div className="section-copy">
          <span className="eyebrow">Live Mesh Visual</span>
          <h2>A network field built around peers, relays, and motion.</h2>
          <p>
            This visual layer represents CoreLink as a live mesh: peers joining,
            links stabilizing, and emergency traffic moving through the graph.
          </p>
        </div>

        <div className="network-panel">
          <div className="network-grid">
            <span className="network-link link-a"></span>
            <span className="network-link link-b"></span>
            <span className="network-link link-c"></span>
            <span className="network-link link-d"></span>
            <span className="network-link link-e"></span>
            <span className="network-link link-f"></span>
            <div className="network-peer peer-anchor"><b>Core</b><small>Relay</small></div>
            <div className="network-peer peer-one"><b>A1</b><small>Voice</small></div>
            <div className="network-peer peer-two"><b>B4</b><small>Alert</small></div>
            <div className="network-peer peer-three"><b>C2</b><small>Location</small></div>
            <div className="network-peer peer-four"><b>D9</b><small>Chat</small></div>
            <div className="network-peer peer-five"><b>E7</b><small>Call</small></div>
          </div>
        </div>
      </section>

      <section className="feature-grid" id="features">
        <article className="feature-card reveal">
          <span className="feature-tag">Chat</span>
          <h2>Mesh Chat</h2>
          <p>Send peer-to-peer messages through Bluetooth connections with a clean Android-first flow.</p>
        </article>
        <article className="feature-card reveal radar-card">
          <span className="feature-tag">Radar</span>
          <h2>Scan Nearby Devices</h2>
          <p>Detect paired and nearby Bluetooth devices with a focused, animated discovery screen.</p>
          <div className="radar-preview">
            <div className="radar-ring ring-one"></div>
            <div className="radar-ring ring-two"></div>
            <div className="radar-core"></div>
          </div>
        </article>
        <article className="feature-card reveal">
          <span className="feature-tag">Voice</span>
          <h2>Voice Lab</h2>
          <p>Turn speech into text, record voice notes, preview them locally, and send tools through an active session.</p>
        </article>
        <article className="feature-card reveal alert-card">
          <span className="feature-tag">Emergency</span>
          <h2>Emergency Center</h2>
          <p>Stage medical, shelter, relay, and location-based help requests with minimal operator friction.</p>
        </article>
        <article className="feature-card reveal call-card">
          <span className="feature-tag">Call</span>
          <h2>Call Interface</h2>
          <p>A dedicated session screen for live-call workflow setup and future audio transport expansion.</p>
        </article>
        <article className="feature-card reveal">
          <span className="feature-tag">Android</span>
          <h2>Made for Phones</h2>
          <p>The website is intentionally lightweight. The real product experience is designed around Android devices in the field.</p>
        </article>
      </section>

      <section className="gallery" id="gallery">
        <div className="section-copy reveal">
          <span className="eyebrow">App Screens</span>
          <h2>The real app flow, screen by screen.</h2>
          <p>These sections mirror the screens you shared: the home hub, chat page, voice lab, emergency center, and call deck.</p>
        </div>

        <div className="gallery-grid">
          <article className="phone-card reveal">
            <div className="phone-shot shot-home">
              <div className="phone-header"><strong>CoreLink</strong><span>Feature Deck</span></div>
              <div className="phone-panel large"><b>Feature Deck</b><small>Mesh chat, device radar, voice tools, emergency actions, and call deck.</small></div>
              <div className="phone-button active">Open Mesh Chat</div>
              <div className="phone-button">Scan Nearby Devices</div>
              <div className="phone-button">Open Voice Lab</div>
              <div className="phone-button">Open Emergency Center</div>
              <div className="phone-button">Open Call Deck</div>
            </div>
            <h3>Home hub</h3>
            <p>One Android-first start screen with clear feature entry points.</p>
          </article>

          <article className="phone-card reveal">
            <div className="phone-shot shot-chat">
              <div className="phone-header"><strong>CoreLink Chat</strong><span>Open and ready. No device connected yet.</span></div>
              <div className="icon-row"><i></i><i></i><i></i><i className="danger"></i></div>
              <div className="message-bubble">Welcome to CoreLink. Use the Bluetooth button to host or join a peer.</div>
              <div className="composer-row"><div className="composer-field">Type a message</div><div className="composer-send"></div></div>
            </div>
            <h3>Mesh chat</h3>
            <p>Shared-session messaging with direct access to connection, voice, alert, and call tools.</p>
          </article>

          <article className="phone-card reveal">
            <div className="phone-shot shot-voice">
              <div className="phone-header"><strong>Voice Lab</strong><span>Microphone idle.</span></div>
              <div className="pulse-panel"><div className="pulse-ring"></div><small>Capture quick notes, playback drafts, and dictate text.</small></div>
              <div className="phone-button active">Voice to Text</div>
              <div className="phone-button">Record Voice Note</div>
              <div className="phone-button">Play Saved Note</div>
              <div className="phone-button">Send Voice Text</div>
              <div className="phone-button">Send Voice Note</div>
              <div className="phone-note">Your dictated text will appear here.</div>
            </div>
            <h3>Voice tools</h3>
            <p>Speech-to-text, voice note recording, playback, and send controls in one page.</p>
          </article>

          <article className="phone-card reveal">
            <div className="phone-shot shot-emergency">
              <div className="phone-header"><strong>Emergency Center</strong><span>Pick a prebuilt alert to stage a rapid response.</span></div>
              <div className="alert-panel"><b>Rapid Alert Console</b><small>Medical, shelter, relay, and location support.</small></div>
              <div className="phone-button alert-main">Send Medical Support Alert</div>
              <div className="phone-button">Send Shelter and Supply Alert</div>
              <div className="phone-button">Send Mesh Relay Alert</div>
              <div className="phone-button">Share Location</div>
              <div className="phone-note">Location not shared yet.</div>
            </div>
            <h3>Emergency center</h3>
            <p>Fast alert staging with structured actions and visible location sharing state.</p>
          </article>

          <article className="phone-card reveal">
            <div className="phone-shot shot-call">
              <div className="phone-header"><strong>Call Deck</strong><span>Call link idle. Choose host or join.</span></div>
              <div className="call-panel"><div className="pulse-ring warm"></div><b>00:00</b></div>
              <div className="phone-button active">Host Call Link</div>
              <div className="phone-button">Join Call Link</div>
              <div className="phone-button">End Call Session</div>
            </div>
            <h3>Call deck</h3>
            <p>A dedicated call session interface with calmer colors and a focused workflow.</p>
          </article>
        </div>
      </section>

      <section className="experience" id="experience">
        <div className="section-copy reveal">
          <span className="eyebrow">Product Experience</span>
          <h2>Focused on the moments that matter.</h2>
          <p>The website mirrors the app&apos;s structure so users understand the product quickly: discover peers, connect, communicate, coordinate, and recover fast.</p>
        </div>
        <div className="timeline-grid">
          {timeline.map(([step, title, text]) => (
            <article key={step} className="timeline-card reveal">
              <span className="timeline-step">{step}</span>
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="docs" id="docs">
        <div className="docs-copy reveal">
          <span className="eyebrow">Documentation</span>
          <h2>Simple docs. Clear actions.</h2>
          <p>The site focuses on Android users first: what the app does, how to install it, and what each feature page is for.</p>
        </div>

        <div className="docs-panels">
          <article className="doc-card reveal">
            <h3>Install</h3>
            <ul>
              <li>Download the APK on Android</li>
              <li>Allow install from browser or file manager</li>
              <li>Open CoreLink and grant Bluetooth, voice, and location permissions</li>
            </ul>
          </article>
          <article className="doc-card reveal">
            <h3>Connection Flow</h3>
            <ul>
              <li>Host a Bluetooth room or join a paired peer</li>
              <li>Use Device Radar to inspect nearby nodes</li>
              <li>Use chat, voice tools, and emergency actions through the same session</li>
            </ul>
          </article>
          <article className="doc-card reveal">
            <h3>Operational Notes</h3>
            <ul>
              <li>Android is the primary platform</li>
              <li>Desktop web is informational only</li>
              <li>Direct site installs still use Android&apos;s normal APK installer prompt</li>
            </ul>
          </article>
        </div>

        <div className="install-steps">
          <article className="step-card reveal"><span className="timeline-step">Step 1</span><h3>Download</h3><p>Open the site on Android and tap the APK download button.</p></article>
          <article className="step-card reveal"><span className="timeline-step">Step 2</span><h3>Allow install</h3><p>Approve install from the browser or file manager if Android asks.</p></article>
          <article className="step-card reveal"><span className="timeline-step">Step 3</span><h3>Grant permissions</h3><p>Allow Bluetooth, microphone, and location only when the app requests them.</p></article>
        </div>

        <div className="docs-detail-grid">
          {docsDetail.map(([tag, title, text]) => (
            <article key={title} className="detail-card reveal">
              <span className="feature-tag">{tag}</span>
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="roadmap">
        <div className="section-copy reveal">
          <span className="eyebrow">Roadmap</span>
          <h2>What comes next for CoreLink.</h2>
          <p>Near-term upgrades to move from strong prototype toward a more complete field communication tool.</p>
        </div>
        <div className="roadmap-grid">
          {roadmap.map(([title, text]) => (
            <article key={title} className="roadmap-card reveal">
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="install-panel reveal" id="install">
        <div>
          <span className="eyebrow">Android install</span>
          <h2>Download the app and install it on Android.</h2>
          <p>Tapping the button on an Android phone downloads the APK and opens the normal Android install flow. Browsers cannot silently install apps by themselves.</p>
        </div>
        <div className="install-actions">
          <a className="primary-btn" href={downloadHref} download>Download APK</a>
          <span className="install-note">APK path: <code>public/downloads/corelink-android.apk</code></span>
        </div>
      </section>

      <section className="faq">
        <div className="section-copy reveal">
          <span className="eyebrow">FAQ</span>
          <h2>Quick answers for operators and testers.</h2>
        </div>
        <div className="faq-grid">
          {faq.map(([title, text]) => (
            <article key={title} className="faq-card reveal">
              <h3>{title}</h3>
              <p>{text}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
