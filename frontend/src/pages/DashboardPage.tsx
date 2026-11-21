import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../api";
import type { Dashboard } from "../types";
import { PageHeader, Spinner, Stat, money } from "../ui";

function change(now: number, before: number): string | undefined {
  if (!before) return undefined;
  const pct = Math.round(((now - before) / Math.abs(before)) * 100);
  return `${pct >= 0 ? "▲" : "▼"} ${Math.abs(pct)}% vs. last month`;
}

export default function DashboardPage() {
  const [d, setD] = useState<Dashboard | null>(null);
  useEffect(() => {
    api<Dashboard>("/api/dashboard").then(setD);
  }, []);
  if (!d) return <Spinner />;
  const m = d.currentMonth;
  const p = d.previousMonth;

  return (
    <div className="space-y-6">
      <PageHeader title="Dashboard" subtitle={`${m.month} · all amounts in EUR at the rate of the day of each operation`} />

      <div data-tour="kpis" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Profit this month" value={money(m.profit)} hint={change(m.profit, p.profit)} />
        <Stat label="Turnover" value={money(m.turnover)} hint={change(m.turnover, p.turnover)} />
        <Stat label="Total balance now" value={money(d.totalBalanceEur)} hint={`${d.accounts.length} accounts in 4 currencies`} />
        <Stat label="Needs attention" value={<span className={d.openAnomalies ? "text-red-700" : ""}>{d.openAnomalies} anomalies</span>}
          hint={d.pendingTransfers ? `${d.pendingTransfers} transfer(s) in transit: ${money(d.inTransitEur)}` : "No money in transit"} />
      </div>

      <div className="card p-5">
        <h3 className="mb-3 font-semibold">Income and expenses per day</h3>
        <ResponsiveContainer width="100%" height={260}>
          <AreaChart data={[...p.daily, ...m.daily].slice(-45)}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="date" fontSize={11} tickFormatter={(v: string) => v.slice(5)} />
            <YAxis fontSize={11} />
            <Tooltip formatter={(v: number) => money(v)} />
            <Legend />
            <Area type="monotone" dataKey="income" name="Income" stroke="#059669" fill="#05966933" />
            <Area type="monotone" dataKey="expense" name="Expenses" stroke="#dc2626" fill="#dc262622" />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="card p-5">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-semibold">Accounts</h3>
            <Link to="/accounts" className="text-sm text-brand">All accounts →</Link>
          </div>
          <table className="w-full text-sm">
            <tbody>
              {d.accounts.map((a) => (
                <tr key={a.id} className="border-t border-slate-100">
                  <td className="py-2">{a.type === "CASH" ? "💶" : "🏦"} {a.name}</td>
                  <td className="text-right tabular-nums">{money(a.balance, a.currency)}</td>
                  <td className="text-right text-xs text-muted tabular-nums">{a.currency !== "EUR" && money(a.balanceEur)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="card p-5">
          <h3 className="mb-3 font-semibold">This month by office</h3>
          {m.profitByOffice.map((o) => (
            <div key={o.name} className="flex justify-between border-t border-slate-100 py-2 text-sm">
              <span>{o.name}</span><span className={`tabular-nums ${o.amountEur < 0 ? "text-red-700" : ""}`}>{money(o.amountEur)}</span>
            </div>
          ))}
          <h3 className="mb-2 mt-5 font-semibold">Biggest expenses</h3>
          {m.expenseByCategory.slice(0, 5).map((c) => (
            <div key={c.name} className="mb-2">
              <div className="flex justify-between text-sm"><span>{c.name}</span><span className="tabular-nums">{money(c.amountEur)}</span></div>
              <div className="h-1.5 rounded-full bg-slate-100"><div className="h-1.5 rounded-full bg-brand" style={{ width: `${m.expense ? (100 * c.amountEur) / m.expense : 0}%` }} /></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
