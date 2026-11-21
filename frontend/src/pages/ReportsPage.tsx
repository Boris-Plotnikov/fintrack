import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../api";
import type { Account, Monthly } from "../types";
import { Button, ErrorBox, PageHeader, Spinner, Stat, Tag, money, useToast } from "../ui";

const COLORS = ["#1e3a8a", "#f59e0b", "#0ea5e9", "#10b981", "#a855f7", "#ef4444", "#64748b", "#ec4899", "#84cc16", "#14b8a6"];

function monthOptions(): string[] {
  const now = new Date();
  return [0, 1, 2, 3].map((i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  });
}

const SAMPLE = `date;amount;description;counterparty
2026-11-03;-49.90;Software subscription;Atlassian
2026-11-04;1850.00;Consulting invoice 4512;Isar Medical AG
2026-11-05;-12.00;Bank fees November;Erste Testbank`;

export default function ReportsPage() {
  const months = monthOptions();
  const [month, setMonth] = useState(months[1]);
  const [report, setReport] = useState<Monthly | null>(null);
  const [toast, showToast] = useToast();

  useEffect(() => {
    setReport(null);
    api<Monthly>(`/api/reports/monthly?month=${month}`).then(setReport);
  }, [month]);

  return (
    <div className="space-y-6">
      {toast}
      <PageHeader title="Reports" subtitle="A correction counts in the month it was made, so a closed month never changes afterwards."
        actions={<>
          <select className="input w-36" value={month} onChange={(e) => setMonth(e.target.value)}>{months.map((m) => <option key={m}>{m}</option>)}</select>
          <a href={`api/reports/export?month=${month}`} className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium hover:bg-slate-50">⬇ CSV</a>
        </>} />
      {!report ? <Spinner /> : (
        <div data-tour="report" className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Income" value={money(report.income)} />
            <Stat label="Expenses" value={money(report.expense)} />
            <Stat label="Profit" value={<span className={report.profit < 0 ? "text-red-700" : "text-emerald-700"}>{money(report.profit)}</span>} hint={report.income ? `margin ${Math.round((100 * report.profit) / report.income)}%` : undefined} />
            <Stat label="Operations" value={report.operations} />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <div className="card p-5">
              <h3 className="mb-3 font-semibold">Expenses by category</h3>
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie data={report.expenseByCategory} dataKey="amountEur" nameKey="name" innerRadius={55} outerRadius={95} paddingAngle={2}>
                    {report.expenseByCategory.map((c, i) => <Cell key={c.name} fill={COLORS[i % COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(v: number) => money(v)} />
                </PieChart>
              </ResponsiveContainer>
              <div className="flex flex-wrap gap-2">{report.expenseByCategory.map((c, i) => <span key={c.name} className="text-xs"><span className="mr-1 inline-block h-2 w-2 rounded-full" style={{ background: COLORS[i % COLORS.length] }} />{c.name} {money(c.amountEur)}</span>)}</div>
            </div>
            <div className="card p-5">
              <h3 className="mb-3 font-semibold">Profit by office</h3>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={report.profitByOffice}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="name" fontSize={12} />
                  <YAxis fontSize={11} />
                  <Tooltip formatter={(v: number) => money(v)} />
                  <Bar dataKey="amountEur" name="Profit" radius={[4, 4, 0, 0]}>
                    {report.profitByOffice.map((o) => <Cell key={o.name} fill={o.amountEur < 0 ? "#dc2626" : "#1e3a8a"} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
              <h3 className="mb-2 mt-4 font-semibold">Income by category</h3>
              {report.incomeByCategory.map((c) => <div key={c.name} className="flex justify-between border-t border-slate-100 py-1.5 text-sm"><span>{c.name}</span><span className="tabular-nums">{money(c.amountEur)}</span></div>)}
            </div>
          </div>
        </div>
      )}
      <ImportBox onDone={(n) => showToast(`${n} lines imported`)} />
    </div>
  );
}

interface ImportResult {
  imported: number;
  duplicates: number;
  errors: number;
  dryRun: boolean;
  rows: { line: number; date?: string; amount?: number; description: string; category?: string; status: string; error?: string }[];
}

function ImportBox({ onDone }: { onDone: (n: number) => void }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountId, setAccountId] = useState("");
  const [content, setContent] = useState(SAMPLE);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Account[]>("/api/accounts").then((list) => {
      const banks = list.filter((a) => a.type === "BANK");
      setAccounts(banks);
      if (banks[0]) setAccountId(String(banks[0].id));
    });
  }, []);

  const run = async (dryRun: boolean) => {
    setError(null);
    try {
      const r = await api<ImportResult>("/api/import", { method: "POST", body: { accountId: Number(accountId), content, dryRun } });
      setResult(r);
      if (!dryRun) onDone(r.imported);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const readFile = (file: File) => file.text().then(setContent);

  return (
    <div className="card p-5">
      <h3 className="font-semibold">Import a bank statement (CSV)</h3>
      <p className="mb-3 text-sm text-muted">Format: <code>date;amount;description;counterparty</code>. Check first, then import. Lines already imported are skipped.</p>
      <div className="grid gap-3 md:grid-cols-3">
        <select className="input" value={accountId} onChange={(e) => setAccountId(e.target.value)}>
          {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
        </select>
        <input type="file" accept=".csv,text/csv" className="text-sm md:col-span-2" onChange={(e) => e.target.files?.[0] && readFile(e.target.files[0])} />
      </div>
      <textarea className="input mt-3 h-32 font-mono text-xs" value={content} onChange={(e) => setContent(e.target.value)} />
      {error && <div className="mt-2"><ErrorBox message={error} /></div>}
      <div className="mt-3 flex gap-2">
        <Button variant="secondary" onClick={() => run(true)}>Check</Button>
        <Button onClick={() => run(false)} disabled={!result || !result.dryRun || result.imported === 0}>Import</Button>
      </div>
      {result && (
        <div className="mt-4">
          <div className="mb-2 flex gap-2 text-xs">
            <Tag tone="green">{result.imported} {result.dryRun ? "ready" : "imported"}</Tag>
            <Tag tone="amber">{result.duplicates} duplicates</Tag>
            <Tag tone="red">{result.errors} errors</Tag>
          </div>
          <table className="w-full text-xs">
            <tbody>
              {result.rows.map((r) => (
                <tr key={r.line} className="border-t border-slate-100">
                  <td className="py-1 text-muted">line {r.line}</td><td>{r.date}</td><td className="tabular-nums">{r.amount}</td>
                  <td>{r.description}</td><td>{r.category}</td>
                  <td><Tag tone={r.status === "ERROR" ? "red" : r.status === "DUPLICATE" ? "amber" : "green"}>{r.status.toLowerCase()}</Tag> {r.error}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
