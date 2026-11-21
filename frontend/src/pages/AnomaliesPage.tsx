import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { Anomaly } from "../types";
import { Button, Empty, PageHeader, Spinner, Tag, dateTime, useToast } from "../ui";

export default function AnomaliesPage() {
  const [filter, setFilter] = useState<"" | "NEW" | "REVIEWED" | "RESOLVED">("");
  const [items, setItems] = useState<Anomaly[] | null>(null);
  const [toast, showToast] = useToast();

  const load = useCallback(() => {
    api<Anomaly[]>(`/api/anomalies${filter ? `?status=${filter}` : ""}`).then(setItems);
  }, [filter]);
  useEffect(() => {
    load();
  }, [load]);

  const decide = async (a: Anomaly, status: "REVIEWED" | "RESOLVED") => {
    const note = status === "RESOLVED" ? prompt("How was it resolved?") : null;
    if (status === "RESOLVED" && !note) return;
    try {
      await api(`/api/anomalies/${a.id}/decision`, { method: "POST", body: { status, note } });
      showToast(status === "RESOLVED" ? "Resolved" : "Marked as reviewed");
      load();
    } catch (e) {
      showToast((e as Error).message);
    }
  };

  return (
    <div>
      {toast}
      <PageHeader title="Anomalies" subtitle="Found automatically: large amounts, late or large corrections, cash differences, overdrafts."
        actions={
          <select className="input w-40" value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)}>
            <option value="">All</option><option value="NEW">New</option><option value="REVIEWED">Reviewed</option><option value="RESOLVED">Resolved</option>
          </select>
        } />
      <div data-tour="anomalies">
        {!items ? <Spinner /> : items.length === 0 ? <Empty title="Nothing here" /> : (
          <div className="space-y-3">
            {items.map((a) => (
              <div key={a.id} className={`card p-4 ${a.status === "NEW" ? "border-l-4 border-l-red-500" : ""}`}>
                <div className="flex flex-wrap items-center gap-2">
                  <Tag tone={a.severity === "HIGH" ? "red" : "amber"}>{a.severity.toLowerCase()}</Tag>
                  <Tag>{a.type.replace(/_/g, " ").toLowerCase()}</Tag>
                  <Tag tone={a.status === "RESOLVED" ? "green" : a.status === "REVIEWED" ? "brand" : "slate"}>{a.status.toLowerCase()}</Tag>
                  <span className="ml-auto text-xs text-muted">{dateTime(a.createdAt)}</span>
                </div>
                <p className="mt-2 text-sm">{a.message}</p>
                {a.resolutionNote && <p className="mt-1 text-xs text-muted">{a.resolvedBy?.fullName}: {a.resolutionNote}</p>}
                {a.status !== "RESOLVED" && (
                  <div className="mt-3 flex gap-2">
                    {a.status === "NEW" && <Button small variant="secondary" onClick={() => decide(a, "REVIEWED")}>Mark reviewed</Button>}
                    <Button small onClick={() => decide(a, "RESOLVED")}>Resolve…</Button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
