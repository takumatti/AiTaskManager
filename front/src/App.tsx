// src/App.tsx
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import ProtectedRoute from "./components/ProtectedRoute";
import GuestRoute from "./components/GuestRoute";
import AiSetup from "./pages/AiSetup";
import StripeSetup from "./pages/StripeSetup";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 未ログインでのみ見せるページ */}
        <Route
          path="/login"
          element={
            <GuestRoute>
              <Login />
            </GuestRoute>
          }
        />

        {/* ログイン必須ページ */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />

  {/* ドキュメント（AI設定手順） */}
        <Route path="/docs/ai-setup" element={<AiSetup />} />
  {/* ドキュメント（Stripe導入手順） */}
  <Route path="/docs/stripe-setup" element={<StripeSetup />} />
      </Routes>
    </BrowserRouter>
  );
}