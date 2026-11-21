import { useEffect, useState } from "react";
import { api } from "../api";
import type { AuditEntry, Page } from "../types";
import { Button, PageHeader, Spinner, dateTime } from "../ui";

export default function AuditPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<AuditEntry> | null>(null);
  useEffect(() => {
    api<Page<AuditEntry>>(`/api/audit?page=${page}`).then(setData);
  }, [page]);
  if (!data) return <Spinner />;
  const pages = data.page?.totalPages ?? data.totalPages ?? 1;
  return (
    <div>
      <PageHeader title="Audit log" subtitle="Append-only record of every change: who, what, when." />
      <div className="card overflow-x-auto">
        <table className="w-full min-w-[640px] text-sm">
          <thead><tr className="text-left text-xs uppercase text-muted"><th className="p-3">When</th><th>Who</th><th>Action</th><th>Details</th></tr></thead>
          <tbody>
            {data.content.map((a) => (
              <tr key={a.id} className="border-t border-slate-100 align-top">
                <td className="whitespace-nowrap p-3 text-xs text-muted">{dateTime(a.at)}</td>
                <td className="py-3">{a.username}</td>
                <td className="py-3 text-xs font-semibold">{a.action.replace(/_/g, " ").toLowerCase()}{a.entityId ? ` #${a.entityId}` : ""}</td>
                <td className="py-3 pr-3">{a.details}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-4 flex justify-center gap-3 text-sm">
        <Button small variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>← Newer</Button>
        <span className="text-muted">Page {page + 1} of {pages}</span>
        <Button small variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Older →</Button>
      </div>
    </div>
  );
}
