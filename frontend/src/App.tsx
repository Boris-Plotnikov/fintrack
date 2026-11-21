import { useCallback, useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { api } from "./api";
import { MeContext, isManagement } from "./session";
import Tour, { TourStep } from "./Tour";
import UnderTheHood from "./codetour/UnderTheHood";
import type { AuditEntry, Me } from "./types";
import { Spinner, dateTime, useToast } from "./ui";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import OperationsPage from "./pages/OperationsPage";
import AccountsPage from "./pages/AccountsPage";
import TransfersPage from "./pages/TransfersPage";
import ShiftPage from "./pages/ShiftPage";
import AnomaliesPage from "./pages/AnomaliesPage";
import ReportsPage from "./pages/ReportsPage";
import AuditPage from "./pages/AuditPage";

const TOUR: TourStep[] = [
  { route: "/dashboard", target: "kpis", title: "The three numbers first", text: "Profit this month, turnover and the total balance of all accounts in EUR - what an owner looks at every morning.", flow: "dashboard" },
  { route: "/operations", target: "phrase", title: "Write it like a chat message", text: "Type “Paid 35 EUR for taxi from Cash Vienna” and press Enter. The system proposes a card; nothing is saved before you confirm.", flow: "phrase" },
  { route: "/operations", target: "op-list", title: "Nothing is ever deleted", text: "A mistake is fixed with a correction linked to the original. Late or large corrections go to the management as anomalies.", flow: "correction" },
  { route: "/transfers", target: "transfers", title: "Money in transit", text: "A transfer between own accounts leaves the sender at once and arrives only when the receiver confirms it. No double counting.", flow: "transfers" },
  { route: "/shift", target: "shift", title: "Blind cash count", text: "At the end of a shift the employee types the counted cash without seeing the expected amount. Differences above the tolerance need a comment.", flow: "shift" },
  { route: "/anomalies", target: "anomalies", title: "What needs attention", text: "Large amounts, late corrections, cash differences, overdrafts - collected automatically for the owner.", flow: "anomalies" },
  { route: "/reports", target: "report", title: "Month report and CSV", text: "Income and expenses by category and office, export to CSV, import of a bank statement with duplicate check.", flow: "reports" },
];

export default function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [tour, setTour] = useState(false);
  const [activityOpen, setActivityOpen] = useState(false);
  const [toast, showToast] = useToast();
  const location = useLocation();

  const loadMe = useCallback(() => api<Me>("/api/auth/me").then(setMe).catch(() => setMe({ authenticated: false, demoMode: false })), []);
  useEffect(() => {
    loadMe();
  }, [loadMe]);

  if (!me) return <Spinner />;
  if (!me.authenticated) {
    return (
      <>
        <LoginPage onLogin={async () => { await loadMe(); setTour(true); }} />
        <UnderTheHood screen="/login" />
      </>
    );
  }

  const logout = async () => {
    await api("/api/auth/logout", { method: "POST" });
    setMe({ authenticated: false, demoMode: me.demoMode });
  };

  const reset = async () => {
    if (!confirm("Reset all demo data to the starting state?")) return;
    await api("/api/demo/reset", { method: "POST" });
    showToast("Demo data reset");
    setTimeout(() => window.location.reload(), 600);
  };

  const nav = [
    { to: "/dashboard", label: "Dashboard", icon: "📊", show: true },
    { to: "/operations", label: "Operations", icon: "🧾", show: true },
    { to: "/accounts", label: "Accounts", icon: "🏦", show: true },
    { to: "/transfers", label: "Transfers", icon: "🔁", show: true },
    { to: "/shift", label: "Shift", icon: "🕘", show: me.role !== "OWNER" },
    { to: "/anomalies", label: "Anomalies", icon: "🚩", show: isManagement(me) },
    { to: "/reports", label: "Reports", icon: "📈", show: isManagement(me) },
    { to: "/audit", label: "Audit log", icon: "🗒️", show: isManagement(me) },
  ];

  return (
    <MeContext.Provider value={me}>
      {toast}
      {tour && <Tour steps={TOUR.filter((s) => me.role === "OWNER" ? s.route !== "/shift" : isManagement(me) || !["/anomalies", "/reports"].includes(s.route))} onClose={() => setTour(false)} />}
      {me.demoMode && (
        <div className="bg-accent/90 px-4 py-1.5 text-center text-xs font-medium text-ink">
          Demonstration project · fictional company “Alpine Services GmbH” · data resets every night at 03:00
          <button onClick={() => setTour(true)} className="ml-3 underline">Take the tour</button>
          <button onClick={reset} className="ml-3 underline">Reset demo</button>
        </div>
      )}
      <div className="min-h-screen md:flex">
        <aside className="border-b border-slate-200 bg-white md:sticky md:top-0 md:h-screen md:w-60 md:shrink-0 md:border-b-0 md:border-r">
          <div className="flex items-center gap-2 px-5 py-4">
            <img src="./favicon.svg" alt="" className="h-8 w-8" />
            <div>
              <div className="font-bold leading-tight">FinTrack</div>
              <div className="text-xs text-muted">Alpine Services GmbH</div>
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0">
            {nav.filter((n) => n.show).map((item) => (
              <NavLink key={item.to} to={item.to}
                className={({ isActive }) => `flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? "bg-brand/10 text-brand" : "text-slate-600 hover:bg-slate-100"}`}>
                <span>{item.icon}</span>{item.label}
              </NavLink>
            ))}
          </nav>
          <div className="hidden border-t border-slate-100 px-5 py-4 text-xs md:block">
            <div className="font-semibold">{me.fullName}</div>
            <div className="text-muted">{me.role?.toLowerCase()} · {me.office}</div>
            <button onClick={() => setActivityOpen(true)} className="mt-3 block text-brand">Live activity →</button>
            <a href="./swagger-ui.html" className="mt-1 block text-brand">API docs ↗</a>
            <button onClick={logout} className="mt-3 text-muted hover:text-ink">Sign out</button>
          </div>
        </aside>
        <main className="mx-auto w-full max-w-6xl flex-1 p-4 md:p-8">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/operations" element={<OperationsPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/transfers" element={<TransfersPage />} />
            <Route path="/shift" element={<ShiftPage />} />
            <Route path="/anomalies" element={<AnomaliesPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/audit" element={<AuditPage />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </main>
      </div>
      <button onClick={() => setActivityOpen(true)} className="fixed bottom-4 left-4 z-30 rounded-full bg-ink px-4 py-2 text-xs font-medium text-white shadow-lg md:hidden">Activity</button>
      {activityOpen && <ActivityPanel onClose={() => setActivityOpen(false)} />}
      <UnderTheHood screen={location.pathname} />
    </MeContext.Provider>
  );
}

function ActivityPanel({ onClose }: { onClose: () => void }) {
  const [items, setItems] = useState<AuditEntry[]>([]);
  useEffect(() => {
    const load = () => api<{ activity: AuditEntry[] }>("/api/dashboard").then((d) => setItems(d.activity)).catch(() => undefined);
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  }, []);
  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-ink/20" onClick={onClose}>
      <div className="h-full w-full max-w-sm overflow-y-auto bg-white p-5 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-1 flex items-center justify-between">
          <h2 className="font-semibold">Live activity</h2>
          <button onClick={onClose} className="text-muted">✕</button>
        </div>
        <p className="mb-4 text-xs text-muted">Every change is written to the audit log: who, what, when. Updates every 5 seconds.</p>
        <ol className="space-y-3">
          {items.map((a) => (
            <li key={a.id} className="border-l-2 border-brand/30 pl-3">
              <div className="text-xs text-muted">{dateTime(a.at)} · {a.username}</div>
              <div className="text-xs font-semibold">{a.action.replace(/_/g, " ").toLowerCase()}</div>
              <div className="text-sm text-slate-700">{a.details}</div>
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
