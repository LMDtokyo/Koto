const { invoke } = window.__TAURI__?.core ?? {};

/** @returns hex identity public key */
export async function registrationSmoke(): Promise<string> {
  if (!invoke) throw new Error("Tauri invoke unavailable");
  return invoke("crypto_registration_smoke") as Promise<string>;
}
