import { Navigate } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "../context/authContext";

interface Props {
  children: ReactNode;
}

export default function ProtectedRoute({ children }: Props) {
  const { auth } = useAuth();

  if (!auth.accessToken) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
