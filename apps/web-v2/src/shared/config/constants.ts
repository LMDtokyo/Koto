export const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:3000";
export const WS_URL = import.meta.env.VITE_WS_URL ?? "ws://localhost:4000";

export const MAX_MESSAGE_LENGTH = 4000;
export const MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024;
export const MAX_ATTACHMENTS_PER_MESSAGE = 10;
export const MAX_USERNAME_LENGTH = 32;
export const MAX_GUILD_NAME_LENGTH = 100;
export const MAX_CHANNEL_NAME_LENGTH = 100;
export const MAX_BIO_LENGTH = 190;

export const HEARTBEAT_INTERVAL_FALLBACK = 45_000;
export const GATEWAY_RECONNECT_BASE_DELAY = 1_000;
export const GATEWAY_RECONNECT_MAX_DELAY = 60_000;

export const TYPING_INDICATOR_TTL = 10_000;
export const MESSAGES_PER_PAGE = 50;
