import { runApi } from "@/shared/api/client";
import type { User } from "@/shared/types";

interface LoginRequest {
  email: string;
  password: string;
}

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

interface AuthResponse {
  user: User;
  access_token: string;
}

export function login(data: LoginRequest): Promise<AuthResponse> {
  return runApi<AuthResponse>("/v1/auth/login", { method: "POST", body: data });
}

export function register(data: RegisterRequest): Promise<AuthResponse> {
  return runApi<AuthResponse>("/v1/auth/register", { method: "POST", body: data });
}

export function logout(): Promise<void> {
  return runApi<void>("/v1/auth/logout", { method: "POST", skipAuth: true });
}
