import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { useMe } from "../session";
import { Button, ErrorBox, PageHeader, Spinner, Tag, dateTime, money, useToast } from "../ui";

interface OpenShift {
  shiftId: number;
  openedAt: string;
  office: string;
  accountsToCount: { accountId: number; name: string; currency: string }[];
}

interface CountResult {
  account: string;
  currency: string;
  expected: number;
  counted: number;
  difference: number;
  withinTolerance: boolean;
  comment?: string;
}

interface ShiftRow {
  id: number;
  user: string;
  office: string;
  openedAt: string;
  closedAt?: string;
  status: "OPEN" | "CLOSED";
  counts: CountResult[];
}

export default function ShiftPage() {
  const me = useMe();
  const [current, setCurrent] = useState<{ open: boolean; shift?: OpenShift } | null>(null);
  const [counted, setCounted] = useState<Record<number, string>>({});
  const [comments, setComments] = useState<Record<number, string>>({});
  const [result, setResult] = useState<{ counts: CountResult[]; hasDiscrepancy: boolean } | null>(null);
  const [history, setHistory] = useState<ShiftRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [toast, showToast] = useToast();

  const load = useCallback(() => {
    api<typeof current>("/api/shifts/current").then(setCurrent);
    api<ShiftRow[]>("/api/shifts").then(setHistory);
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  const open = async () => {
    try {
      await api("/api/shifts/open", { method: "POST" });
      setResult(null);
      showToast("Shift opened - operations are now linked to it");
      load();
    } catch (e) {
      showToast((e as Error).message);
    }
  };

  const close = async () => {
    if (!current?.shift) return;
    setError(null);
    try {
      const r = await api<{ counts: CountResult[]; hasDiscrepancy: boolean }>(`/api/shifts/${current.shift.shiftId}/close`, {
        method: "POST",
        body: { counts: current.shift.accountsToCount.map((a) => ({ accountId: a.accountId, counted: Number((counted[a.accountId] ?? "").replace(",", ".")), comment: comments[a.accountId] })) },
      });
      setResult(r);
      setCounted({});
      setComments({});
      load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  if (!current) return <Spinner />;

  return (
    <div>
      {toast}
      <PageHeader title="Shift" subtitle={`${me.fullName} · ${me.office}`} />
      <div data-tour="shift" className="card mb-6 p-6">
        {!current.open ? (
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <div className="font-semibold">No open shift</div>
              <p className="text-sm text-muted">Open a shift at the start of the day. At the end you count the cash box “blind”.</p>
            </div>
            <Button onClick={open}>Open shift</Button>
          </div>
        ) : (
          <div>
            <div className="mb-1 font-semibold">Shift open since {dateTime(current.shift!.openedAt)}</div>
            <p className="mb-4 text-sm text-muted">
              Count the cash and type the real amount. The expected amount is hidden on purpose: you see the comparison only after closing.
            </p>
            <div className="space-y-3">
              {current.shift!.accountsToCount.map((a) => (
                <div key={a.accountId} className="grid gap-2 sm:grid-cols-3">
                  <div className="self-center font-medium">💶 {a.name}</div>
                  <input className="input" inputMode="decimal" placeholder={`Counted ${a.currency}`} value={counted[a.accountId] ?? ""} onChange={(e) => setCounted({ ...counted, [a.accountId]: e.target.value })} />
                  <input className="input" placeholder="Comment if something is off" value={comments[a.accountId] ?? ""} onChange={(e) => setComments({ ...comments, [a.accountId]: e.target.value })} />
                </div>
              ))}
            </div>
            {error && <div className="mt-3"><ErrorBox message={error} /></div>}
            <Button className="mt-4" onClick={close} disabled={current.shift!.accountsToCount.some((a) => !counted[a.accountId])}>Close shift</Button>
          </div>
        )}
        {result && (
          <div className={`mt-6 rounded-lg p-4 ${result.hasDiscrepancy ? "bg-red-50" : "bg-emerald-50"}`}>
            <div className="font-semibold">{result.hasDiscrepancy ? "Closed with a difference - the management is informed" : "Closed. Everything matches 👍"}</div>
            {result.counts.map((c) => (
              <div key={c.account} className="mt-2 text-sm">
                {c.account}: expected {money(c.expected, c.currency)}, counted {money(c.counted, c.currency)}, difference <b>{money(c.difference, c.currency)}</b>
              </div>
            ))}
          </div>
        )}
      </div>

      <h2 className="mb-3 font-semibold">Recent shifts</h2>
      <div className="card divide-y divide-slate-100">
        {history.map((s) => (
          <div key={s.id} className="flex flex-wrap items-center gap-3 p-3 text-sm">
            <div className="w-40">{dateTime(s.openedAt)}</div>
            <div className="flex-1">{s.user} · {s.office}</div>
            {s.counts.map((c) => (
              <Tag key={c.account} tone={c.withinTolerance ? "green" : "red"}>{c.account}: {Number(c.difference) === 0 ? "exact" : money(c.difference, c.currency)}</Tag>
            ))}
            {s.status === "OPEN" && <Tag tone="amber">open</Tag>}
          </div>
        ))}
      </div>
    </div>
  );
}
