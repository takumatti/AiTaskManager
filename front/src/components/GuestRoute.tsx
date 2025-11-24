import { Navigate } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "../context/authContext";

interface Props {
  children: ReactNode;
}

export default function GuestRoute({ children }: Props) {
  const { auth } = useAuth();

  // ログイン済みなら dashboard にリダイレクト
  if (auth.accessToken) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
}