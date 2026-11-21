import { useEffect, useState } from "react";
import { api } from "../api";
import type { Account, Operation, Page } from "../types";
import { Amount, PageHeader, Spinner, Tag, dateTime, money } from "../ui";

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[] | null>(null);
  const [selected, setSelected] = useState<Account | null>(null);
  const [history, setHistory] = useState<Operation[]>([]);

  useEffect(() => {
    api<Account[]>("/api/accounts").then((list) => {
      setAccounts(list);
      setSelected(list[0] ?? null);
    });
  }, []);

  useEffect(() => {
    if (selected) api<Page<Operation>>(`/api/operations?accountId=${selected.id}&size=15`).then((p) => setHistory(p.content));
  }, [selected]);

  if (!accounts) return <Spinner />;
  const offices = [...new Set(accounts.map((a) => a.office))];

  return (
    <div>
      <PageHeader title="Accounts" subtitle="Balance = opening balance + all operations. It is calculated, never typed in, so it cannot drift." />
      <div className="grid gap-6 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-2">
          {offices.map((office) => (
            <div key={office}>
              <div className="label">{office}</div>
              <div className="space-y-2">
                {accounts.filter((a) => a.office === office).map((a) => (
                  <button key={a.id} onClick={() => setSelected(a)}
                    className={`card flex w-full items-center justify-between p-3 text-left transition ${selected?.id === a.id ? "border-brand ring-2 ring-brand/20" : "hover:border-slate-300"}`}>
                    <div>
                      <div className="font-medium">{a.type === "CASH" ? "💶" : "🏦"} {a.name}</div>
                      <div className="text-xs text-muted">manager {a.manager}</div>
                    </div>
                    <div className="text-right">
                      <div className={`font-semibold tabular-nums ${a.balance < 0 ? "text-red-700" : ""}`}>{money(a.balance, a.currency)}</div>
                      {a.currency !== "EUR" && <div className="text-xs text-muted">{money(a.balanceEur)}</div>}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
        {selected && (
          <div className="card p-5 lg:col-span-3">
            <h2 className="text-lg font-semibold">{selected.name}</h2>
            <div className="mt-1 flex flex-wrap gap-2 text-xs">
              <Tag tone="brand">{selected.type.toLowerCase()}</Tag><Tag>{selected.currency}</Tag>
              <Tag>owner {selected.owner}</Tag><Tag>manager {selected.manager}</Tag>
              {selected.type === "CASH" && <Tag tone="amber">count tolerance ±{money(selected.tolerance, selected.currency)}</Tag>}
            </div>
            <div className="mt-4 text-3xl font-bold tabular-nums">{money(selected.balance, selected.currency)}</div>
            <h3 className="mb-2 mt-6 text-sm font-semibold">Latest operations</h3>
            <div className="divide-y divide-slate-100 text-sm">
              {history.map((o) => (
                <div key={o.id} className="flex justify-between gap-3 py-2">
                  <div className="min-w-0"><div className="truncate">{o.description}</div><div className="text-xs text-muted">{dateTime(o.operationAt)} · {o.createdBy}</div></div>
                  <Amount value={o.effectiveAmount} currency={o.currency} />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
