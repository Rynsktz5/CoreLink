"use client";

import { useEffect } from "react";
import anime from "animejs";

export default function AnimatedSiteEffects() {
  useEffect(() => {
    const revealTargets = document.querySelectorAll(".reveal");

    anime({
      targets: revealTargets,
      opacity: [0, 1],
      translateY: [24, 0],
      easing: "easeOutExpo",
      duration: 1100,
      delay: anime.stagger(80),
    });

    anime({
      targets: ".orbit-card",
      translateY: [-6, 6],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 2600,
      delay: anime.stagger(180),
    });

    anime({
      targets: ".mesh-ribbon",
      rotateZ: [0, 360],
      loop: true,
      easing: "linear",
      duration: 18000,
    });

    anime({
      targets: ".mesh-node",
      scale: [0.88, 1.18],
      opacity: [0.72, 1],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 1800,
      delay: anime.stagger(160),
    });

    anime({
      targets: ".mesh-beam",
      strokeDashoffset: [anime.setDashoffset, 0],
      opacity: [0.2, 1],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 2600,
      delay: anime.stagger(120),
    });

    anime({
      targets: ".hero-glow",
      scale: [0.92, 1.08],
      opacity: [0.65, 1],
      loop: true,
      direction: "alternate",
      easing: "easeInOutSine",
      duration: 3200,
    });

    anime({
      targets: ".ring-one",
      scale: [0.72, 1.08],
      opacity: [0.25, 0.7],
      loop: true,
      easing: "easeOutSine",
      duration: 2200,
    });

    anime({
      targets: ".ring-two",
      scale: [0.48, 0.9],
      opacity: [0.3, 0.78],
      loop: true,
      easing: "easeOutSine",
      duration: 2200,
      delay: 320,
    });

    anime({
      targets: ".metric-pill",
      translateY: [-3, 3],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 2400,
      delay: anime.stagger(120),
    });

    anime({
      targets: ".mock-radar b",
      scale: [0.92, 1.06],
      opacity: [0.3, 0.9],
      loop: true,
      direction: "alternate",
      easing: "easeInOutSine",
      duration: 2000,
      delay: anime.stagger(220),
    });

    anime({
      targets: ".floating-install",
      translateY: [10, 0],
      opacity: [0, 1],
      easing: "easeOutExpo",
      duration: 1400,
      delay: 700,
    });

    anime({
      targets: ".pulse-ring",
      scale: [0.92, 1.06],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 1800,
    });

    anime({
      targets: ".message-bubble",
      translateY: [-3, 3],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 2600,
    });

    anime({
      targets: ".network-peer",
      translateY: [-8, 8],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 2600,
      delay: anime.stagger(140),
    });

    anime({
      targets: ".network-link",
      opacity: [0.18, 1],
      scaleX: [0.82, 1.06],
      direction: "alternate",
      loop: true,
      easing: "easeInOutSine",
      duration: 2200,
      delay: anime.stagger(120),
    });

    const onScroll = () => {
      const heroVisual = document.querySelector(".hero-visual");
      const meshStage = document.querySelector(".mesh-stage");
      const networkPanel = document.querySelector(".network-panel");

      if (window.innerWidth <= 768) {
        if (heroVisual) heroVisual.style.transform = "";
        if (meshStage) meshStage.style.transform = "";
        if (networkPanel) networkPanel.style.transform = "";
        return;
      }

      const offset = window.scrollY;
      if (heroVisual) heroVisual.style.transform = `translateY(${offset * -0.03}px)`;
      if (meshStage) meshStage.style.transform = `translateY(${offset * -0.06}px) rotate(${offset * 0.02}deg)`;
      if (networkPanel) networkPanel.style.transform = `translateY(${offset * -0.018}px)`;
    };

    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();

    return () => {
      window.removeEventListener("scroll", onScroll);
      anime.remove("*");
    };
  }, []);

  return null;
}
