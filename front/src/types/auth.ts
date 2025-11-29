export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  username?: string;
  userId?: number;
}

export interface AuthContextProps {
  auth: AuthState;
  setAuth: (auth: AuthState) => void;
  logout: () => void;
  error: string;
  setError: (msg: string) => void;
  refreshToken: () => Promise<void>;
}