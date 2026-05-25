import { downloadHref } from "./siteData";

export default function FloatingInstall() {
  return (
    <a className="floating-install" href={downloadHref} download>
      Install on Android
    </a>
  );
}
