import { FormEvent, useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { isManagement, useMe } from "../session";
import type { Account, Transfer } from "../types";
import { Button, ErrorBox, PageHeader, Spinner, Tag, dateTime, money, useToast } from "../ui";

export default function TransfersPage() {
  const me = useMe();
  const [transfers, setTransfers] = useState<Transfer[] | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [amount, setAmount] = useState("");
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [toast, showToast] = useToast();

  const load = useCallback(() => {
    api<Transfer[]>("/api/transfers").then(setTransfers);
    api<Account[]>("/api/accounts").then(setAccounts);
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api("/api/transfers", { method: "POST", body: { fromAccountId: Number(from), toAccountId: Number(to), amount: Number(amount.replace(",", ".")), comment } });
      setAmount("");
      setComment("");
      showToast("Transfer sent - waiting for confirmation");
      load();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const decide = async (t: Transfer, action: "confirm" | "reject") => {
    try {
      if (action === "reject") {
        const reason = prompt("Why is the transfer rejected?");
        if (!reason) return;
        await api(`/api/transfers/${t.id}/reject`, { method: "POST", body: { reason } });
      } else {
        await api(`/api/transfers/${t.id}/confirm`, { method: "POST" });
      }
      showToast(action === "confirm" ? "Money received" : "Rejected, money returned to the sender");
      load();
    } catch (err) {
      showToast((err as Error).message);
    }
  };

  if (!transfers) return <Spinner />;
  const fromOptions = accounts.filter((a) => me.role !== "EMPLOYEE" || a.office === me.office);
  const fromAccount = accounts.find((a) => String(a.id) === from);
  const toAccount = accounts.find((a) => String(a.id) === to);

  return (
    <div>
      {toast}
      <PageHeader title="Transfers" subtitle="Between own accounts. The sender records it, the receiver confirms when the money is really there." />
      <form onSubmit={create} className="card mb-6 grid gap-3 p-4 md:grid-cols-5">
        <select className="input" value={from} onChange={(e) => setFrom(e.target.value)} required>
          <option value="">From…</option>
          {fromOptions.map((a) => <option key={a.id} value={a.id}>{a.name} ({money(a.balance, a.currency)})</option>)}
        </select>
        <select className="input" value={to} onChange={(e) => setTo(e.target.value)} required>
          <option value="">To…</option>
          {accounts.filter((a) => String(a.id) !== from).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
        </select>
        <input className="input" inputMode="decimal" placeholder={`Amount ${fromAccount ? fromAccount.currency : ""}`} value={amount} onChange={(e) => setAmount(e.target.value)} required />
        <input className="input" placeholder="Comment" value={comment} onChange={(e) => setComment(e.target.value)} />
        <Button type="submit">Send</Button>
        {fromAccount && toAccount && fromAccount.currency !== toAccount.currency && (
          <p className="text-xs text-muted md:col-span-5">Different currencies: the amount is converted at today’s rate and the receiver gets {toAccount.currency}.</p>
        )}
        {error && <div className="md:col-span-5"><ErrorBox message={error} /></div>}
      </form>

      <div data-tour="transfers" className="card divide-y divide-slate-100">
        {transfers.map((t) => {
          const canDecide = t.status === "PENDING" && (t.toManagerId === me.id || isManagement(me));
          return (
            <div key={t.id} className="flex flex-wrap items-center gap-4 p-4">
              <div className="min-w-0 flex-1">
                <div className="font-medium">{t.fromAccount} → {t.toAccount}</div>
                <div className="text-xs text-muted">#{t.id} · {dateTime(t.createdAt)} by {t.createdBy}{t.comment && ` · ${t.comment}`}</div>
              </div>
              <div className="text-right text-sm tabular-nums">
                {money(t.amount, t.fromCurrency)}
                {t.fromCurrency !== t.toCurrency && <div className="text-xs text-muted">= {money(t.amountReceived, t.toCurrency)}</div>}
              </div>
              <Tag tone={t.status === "PENDING" ? "amber" : t.status === "CONFIRMED" ? "green" : "red"}>
                {t.status === "PENDING" ? `in transit · ${t.toManager} confirms` : t.status.toLowerCase()}
              </Tag>
              {canDecide && (
                <div className="flex gap-2">
                  <Button small onClick={() => decide(t, "confirm")}>Money arrived</Button>
                  <Button small variant="danger" onClick={() => decide(t, "reject")}>Reject</Button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
