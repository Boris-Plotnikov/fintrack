import { FormEvent, useCallback, useEffect, useState } from "react";
import { api, query } from "../api";
import { isManagement, useMe } from "../session";
import type { Account, Category, Draft, Operation, OperationKind, Page } from "../types";
import { Amount, Button, Empty, ErrorBox, Modal, PageHeader, Spinner, Tag, dateTime, money, useToast } from "../ui";

const KIND_LABEL: Record<OperationKind, string> = {
  INCOME: "Income", EXPENSE: "Expense", OWNER_DEPOSIT: "Owner deposit", OWNER_WITHDRAWAL: "Owner withdrawal",
  TRANSFER_OUT: "Transfer out", TRANSFER_IN: "Transfer in", CORRECTION: "Correction",
};

export default function OperationsPage() {
  const me = useMe();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [filters, setFilters] = useState({ accountId: "", kind: "", search: "", from: "", to: "" });
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Operation> | null>(null);
  const [phrase, setPhrase] = useState("");
  const [draft, setDraft] = useState<Draft | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [correcting, setCorrecting] = useState<Operation | null>(null);
  const [toast, showToast] = useToast();

  const load = useCallback(() => {
    api<Page<Operation>>(`/api/operations${query({ ...filters, page, size: 25 })}`).then(setData);
  }, [filters, page]);

  useEffect(() => {
    const t = setTimeout(load, 200);
    return () => clearTimeout(t);
  }, [load]);

  useEffect(() => {
    api<Account[]>("/api/accounts").then(setAccounts);
    api<Category[]>("/api/categories").then(setCategories);
  }, []);

  const parse = async (e: FormEvent) => {
    e.preventDefault();
    if (!phrase.trim()) return;
    setDraft(await api<Draft>("/api/operations/parse", { method: "POST", body: { text: phrase } }));
  };

  const myAccounts = accounts.filter((a) => me.role !== "EMPLOYEE" || a.office === me.office);
  const pages = data?.page?.totalPages ?? data?.totalPages ?? 0;

  return (
    <div>
      {toast}
      <PageHeader title="Operations" subtitle="Every movement of money. Mistakes are corrected, never deleted."
        actions={<Button onClick={() => setFormOpen(true)}>+ New operation</Button>} />

      <form onSubmit={parse} data-tour="phrase" className="card mb-6 p-4">
        <label className="label">Quick entry - write it like a message</label>
        <div className="flex gap-2">
          <input className="input" value={phrase} onChange={(e) => setPhrase(e.target.value)}
            placeholder={me.role === "EMPLOYEE" ? `Paid 35 EUR for taxi from Cash ${me.office}` : "Received 2500 EUR from Isar Medical AG for consulting to Bank Vienna"} />
          <Button type="submit" disabled={!phrase.trim()}>Read</Button>
        </div>
        <div className="mt-2 flex flex-wrap gap-2 text-xs text-muted">
          Try:
          {["Paid 18,50 EUR for coffee, cash vienna", "Received 1.200 EUR from Donau Logistik GmbH for training to Bank Vienna", "spent 450 on train tickets, cash prague"].map((s) => (
            <button type="button" key={s} onClick={() => setPhrase(s)} className="rounded bg-slate-100 px-2 py-0.5 hover:bg-slate-200">{s}</button>
          ))}
        </div>
      </form>

      <div className="card mb-4 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <input className="input lg:col-span-2" placeholder="Search description or counterparty" value={filters.search} onChange={(e) => { setFilters({ ...filters, search: e.target.value }); setPage(0); }} />
        <select className="input" value={filters.accountId} onChange={(e) => { setFilters({ ...filters, accountId: e.target.value }); setPage(0); }}>
          <option value="">All accounts</option>
          {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
        </select>
        <select className="input" value={filters.kind} onChange={(e) => { setFilters({ ...filters, kind: e.target.value }); setPage(0); }}>
          <option value="">All kinds</option>
          {(["INCOME", "EXPENSE", "TRANSFER_OUT", "TRANSFER_IN", "OWNER_DEPOSIT", "OWNER_WITHDRAWAL"] as OperationKind[]).map((k) => <option key={k} value={k}>{KIND_LABEL[k]}</option>)}
        </select>
        <input type="date" className="input" value={filters.from} onChange={(e) => { setFilters({ ...filters, from: e.target.value }); setPage(0); }} />
      </div>

      <div data-tour="op-list">
        {!data ? <Spinner /> : data.content.length === 0 ? <Empty title="No operations for these filters" /> : (
          <div className="card divide-y divide-slate-100">
            {data.content.map((o) => (
              <div key={o.id} className={`flex flex-wrap items-start gap-3 p-4 ${o.status === "CANCELLED" ? "opacity-60" : ""}`}>
                <div className="w-28 shrink-0 text-xs text-muted">{dateTime(o.operationAt)}<br />#{o.id}</div>
                <div className="min-w-0 flex-1">
                  <div className={`font-medium ${o.status === "CANCELLED" ? "line-through" : ""}`}>{o.description}</div>
                  <div className="text-xs text-muted">
                    {KIND_LABEL[o.kind]}{o.category && ` · ${o.category}`} · {o.account}{o.counterparty && ` · ${o.counterparty}`} · by {o.createdBy}
                  </div>
                  {o.corrections.length > 0 && (
                    <ul className="mt-2 space-y-1 border-l-2 border-amber-300 pl-3 text-xs">
                      {o.corrections.map((c) => (
                        <li key={c.id}>✏️ <Amount value={c.delta} currency={o.currency} /> - {c.reason} <span className="text-muted">({c.by}, {dateTime(c.at)})</span></li>
                      ))}
                    </ul>
                  )}
                </div>
                <div className="text-right">
                  <Amount value={o.effectiveAmount} currency={o.currency} strong />
                  {o.currency !== "EUR" && <div className="text-xs text-muted">{money(o.effectiveAmountEur)}</div>}
                  {o.status !== "ACTIVE" && <div className="mt-1"><Tag tone={o.status === "CANCELLED" ? "red" : "amber"}>{o.status.toLowerCase()}</Tag></div>}
                  {["INCOME", "EXPENSE", "OWNER_DEPOSIT", "OWNER_WITHDRAWAL"].includes(o.kind) && o.status !== "CANCELLED" && (isManagement(me) || o.createdBy === me.fullName) && (
                    <button onClick={() => setCorrecting(o)} className="mt-1 block w-full text-right text-xs text-brand">Correct</button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
        {pages > 1 && (
          <div className="mt-4 flex items-center justify-center gap-3 text-sm">
            <Button small variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>← Newer</Button>
            <span className="text-muted">Page {page + 1} of {pages}</span>
            <Button small variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Older →</Button>
          </div>
        )}
      </div>

      {(draft || formOpen) && (
        <OperationForm draft={draft} accounts={myAccounts} categories={categories} isOwner={me.role === "OWNER"}
          onClose={() => { setDraft(null); setFormOpen(false); }}
          onSaved={(o) => { setDraft(null); setFormOpen(false); setPhrase(""); showToast(`Saved #${o.id}`); load(); }} />
      )}
      {correcting && (
        <CorrectForm op={correcting} onClose={() => setCorrecting(null)} onSaved={() => { setCorrecting(null); showToast("Correction saved"); load(); }} />
      )}
    </div>
  );
}

function OperationForm({ draft, accounts, categories, isOwner, onClose, onSaved }: {
  draft: Draft | null; accounts: Account[]; categories: Category[]; isOwner: boolean;
  onClose: () => void; onSaved: (o: Operation) => void;
}) {
  const [kind, setKind] = useState<OperationKind>(draft?.kind ?? "EXPENSE");
  const [accountId, setAccountId] = useState<string>(draft?.accountId ? String(draft.accountId) : "");
  const [categoryId, setCategoryId] = useState<string>(draft?.categoryId ? String(draft.categoryId) : "");
  const [amount, setAmount] = useState<string>(draft?.amount ? String(draft.amount) : "");
  const [description, setDescription] = useState(draft?.description ?? "");
  const [counterparty, setCounterparty] = useState(draft?.counterparty ?? "");
  const [error, setError] = useState<string | null>(null);
  const account = accounts.find((a) => String(a.id) === accountId);
  const needsCategory = kind === "INCOME" || kind === "EXPENSE";

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const o = await api<Operation>("/api/operations", { method: "POST", body: {
        kind, accountId: Number(accountId), categoryId: needsCategory ? Number(categoryId) : null,
        amount: Number(amount.replace(",", ".")), description, counterparty, source: draft ? "PHRASE" : "FORM",
      } });
      onSaved(o);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <Modal title={draft ? "Check and confirm" : "New operation"} onClose={onClose}>
      {draft && draft.warnings.length > 0 && (
        <div className="mb-3 rounded-lg bg-amber-50 p-3 text-xs text-amber-900">{draft.warnings.map((w) => <div key={w}>⚠️ {w}</div>)}</div>
      )}
      <form onSubmit={save} className="space-y-3">
        <div className="flex gap-2">
          {(["EXPENSE", "INCOME", ...(isOwner ? ["OWNER_DEPOSIT", "OWNER_WITHDRAWAL"] : [])] as OperationKind[]).map((k) => (
            <button type="button" key={k} onClick={() => setKind(k)} className={`rounded-lg border px-3 py-1 text-xs ${kind === k ? "border-brand bg-brand text-white" : "border-slate-300"}`}>{KIND_LABEL[k]}</button>
          ))}
        </div>
        <div>
          <label className="label">Account</label>
          <select className="input" value={accountId} onChange={(e) => setAccountId(e.target.value)} required>
            <option value="" disabled>Choose…</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({money(a.balance, a.currency)})</option>)}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label">Amount {account && `(${account.currency})`}</label>
            <input className="input" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          </div>
          {needsCategory && (
            <div>
              <label className="label">Category</label>
              <select className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
                <option value="" disabled>Choose…</option>
                {categories.filter((c) => c.kind === kind).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
          )}
        </div>
        <div>
          <label className="label">Description</label>
          <input className="input" value={description} onChange={(e) => setDescription(e.target.value)} required maxLength={300} />
        </div>
        <div>
          <label className="label">Counterparty (optional)</label>
          <input className="input" value={counterparty} onChange={(e) => setCounterparty(e.target.value)} maxLength={120} />
        </div>
        {error && <ErrorBox message={error} />}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit">Confirm and save</Button>
        </div>
      </form>
    </Modal>
  );
}

function CorrectForm({ op, onClose, onSaved }: { op: Operation; onClose: () => void; onSaved: () => void }) {
  const [amount, setAmount] = useState(String(Math.abs(op.effectiveAmount)));
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const save = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await api(`/api/operations/${op.id}/correct`, { method: "POST", body: { newAmount: Number(amount.replace(",", ".")), reason } });
      onSaved();
    } catch (err) {
      setError((err as Error).message);
    }
  };
  return (
    <Modal title={`Correct operation #${op.id}`} onClose={onClose}>
      <p className="mb-3 text-sm text-muted">The original stays in the history. A correction with the difference is added and linked to it. Set 0 to cancel the operation.</p>
      <form onSubmit={save} className="space-y-3">
        <div className="rounded-lg bg-slate-50 p-3 text-sm">{op.description} · now <b>{money(Math.abs(op.effectiveAmount), op.currency)}</b></div>
        <div>
          <label className="label">Correct amount ({op.currency})</label>
          <input className="input" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </div>
        <div>
          <label className="label">Reason</label>
          <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. receipt shows 42.80" required maxLength={200} />
        </div>
        {error && <ErrorBox message={error} />}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit">Save correction</Button>
        </div>
      </form>
    </Modal>
  );
}
