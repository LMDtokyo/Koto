export const UserStatus = {
  Online: "online",
  Idle: "idle",
  Dnd: "dnd",
  Offline: "offline",
} as const;

export type UserStatus = (typeof UserStatus)[keyof typeof UserStatus];

export interface User {
  id: string;
  username: string;
  discriminator: string;
  avatar: string | null;
  bio: string | null;
  status: UserStatus;
  flags: number;
  created_at: string;
}

export interface UserSettings {
  theme: "dark" | "light";
  locale: string;
  message_display: "cozy" | "compact";
  notifications_enabled: boolean;
}
