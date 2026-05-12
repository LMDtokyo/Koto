import { registrationSmoke } from "@/shared/services/cryptoService";

export function initWelcomeActions(): void {
  const btn = document.getElementById("welcome-crypto-btn");
  const out = document.getElementById("welcome-crypto-out");
  if (!btn || !out) return;

  btn.addEventListener("click", async () => {
    out.textContent = "…";
    try {
      const hexPk = await registrationSmoke();
      out.textContent = `identity_public_key (hex): ${hexPk}`;
    } catch (e) {
      out.textContent = String(e);
    }
  });
}
