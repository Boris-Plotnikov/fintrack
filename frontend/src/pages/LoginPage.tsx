import { FormEvent, useEffect, useState } from "react";
import { api } from "../api";
import { Button, ErrorBox } from "../ui";

interface DemoUser {
  username: string;
  password: string;
  fullName: string;
  role: string;
  office: string;
}

const ROLE_TEXT: Record<string, string> = {
  OWNER: "Sees everything: reports, anomalies, audit",
  MANAGER: "Runs the offices, confirms transfers, reviews anomalies",
  EMPLOYEE: "Records daily operations, closes the cash shift",
};

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [demoUsers, setDemoUsers] = useState<DemoUser[]>([]);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<DemoUser[]>("/api/auth/demo-users").then(setDemoUsers).catch(() => undefined);
  }, []);

  const login = async (u: string, p: string) => {
    setBusy(true);
    setError(null);
    try {
      await api("/api/auth/login", { method: "POST", body: { username: u, password: p } });
      onLogin();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    login(username, password);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-brand/10 via-surface to-accent/10 p-4">
      <div className="mx-auto max-w-3xl py-10">
        <div className="flex items-center gap-3">
          <img src="./favicon.svg" alt="" className="h-10 w-10" />
          <div className="text-2xl font-bold">FinTrack</div>
        </div>
        <h1 className="mt-6 text-3xl font-bold leading-tight">Accounting for a company with three offices and four currencies</h1>
        <p className="mt-2 max-w-2xl text-muted">
          Demonstration project for the fictional “Alpine Services GmbH” (Vienna, Munich, Prague). Based on a real accounting system built for a client; all data here is invented.
        </p>

        {demoUsers.length > 0 && (
          <div className="mt-8">
            <div className="label">Sign in with one click</div>
            <div className="grid gap-3 sm:grid-cols-2">
              {demoUsers.map((u) => (
                <button key={u.username} disabled={busy} onClick={() => login(u.username, u.password)}
                  className="card flex flex-col items-start p-4 text-left transition hover:border-brand hover:shadow-md disabled:opacity-50">
                  <span className="text-xs font-semibold uppercase tracking-wide text-brand">{u.role.toLowerCase()} · {u.office}</span>
                  <span className="mt-1 font-semibold">{u.fullName}</span>
                  <span className="mt-1 text-xs text-muted">{ROLE_TEXT[u.role]}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        <form onSubmit={submit} className="card mt-8 max-w-sm space-y-3 p-5">
          <div className="label">Or with a username</div>
          <input className="input" placeholder="Username" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
          <input className="input" type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
          {error && <ErrorBox message={error} />}
          <Button type="submit" disabled={busy || !username || !password}>Sign in</Button>
        </form>
      </div>
    </div>
  );
}
