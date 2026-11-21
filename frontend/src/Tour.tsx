import { useEffect, useLayoutEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { openTour } from "./codetour/store";

export interface TourStep {
  route: string;
  target: string;
  title: string;
  text: string;
  flow?: string;
}

export default function Tour({ steps, onClose }: { steps: TourStep[]; onClose: () => void }) {
  const [index, setIndex] = useState(0);
  const [box, setBox] = useState<DOMRect | null>(null);
  const navigate = useNavigate();
  const step = steps[index];

  useEffect(() => {
    navigate(step.route);
  }, [step, navigate]);

  useLayoutEffect(() => {
    let tries = 0;
    const find = () => {
      const el = document.querySelector(`[data-tour="${step.target}"]`);
      if (el) {
        el.scrollIntoView({ block: "center", behavior: "smooth" });
        setTimeout(() => setBox(el.getBoundingClientRect()), 350);
      } else if (tries++ < 20) {
        setTimeout(find, 150);
      } else {
        setBox(null);
      }
    };
    setBox(null);
    find();
  }, [step]);

  const last = index === steps.length - 1;
  const top = box ? Math.min(box.bottom + 12, window.innerHeight - 190) : window.innerHeight / 2 - 90;
  const left = box ? Math.max(12, Math.min(box.left, window.innerWidth - 352)) : window.innerWidth / 2 - 170;

  return (
    <div className="fixed inset-0 z-50">
      <div className="absolute inset-0 bg-ink/30" onClick={onClose} />
      {box && (
        <div className="pointer-events-none absolute rounded-xl ring-4 ring-accent transition-all"
          style={{ top: box.top - 6, left: box.left - 6, width: box.width + 12, height: box.height + 12, boxShadow: "0 0 0 9999px rgba(15,23,42,0.35)" }} />
      )}
      <div className="card absolute w-[340px] p-4 shadow-xl" style={{ top, left }}>
        <div className="text-xs font-semibold uppercase tracking-wide text-accent">Step {index + 1} of {steps.length}</div>
        <div className="mt-1 font-semibold">{step.title}</div>
        <p className="mt-1 text-sm text-slate-600">{step.text}</p>
        {step.flow && (
          <button className="mt-2 font-mono text-xs font-semibold text-brand hover:underline" onClick={() => { const flow = step.flow!; onClose(); openTour(flow); }}>
            &lt;/&gt; See the code
          </button>
        )}
        <div className="mt-3 flex justify-between">
          <button className="text-sm text-muted" onClick={onClose}>Skip</button>
          <div className="flex gap-2">
            {index > 0 && <button className="rounded-lg border px-3 py-1 text-sm" onClick={() => setIndex(index - 1)}>Back</button>}
            <button className="rounded-lg bg-brand px-3 py-1 text-sm text-white" onClick={() => (last ? onClose() : setIndex(index + 1))}>
              {last ? "Finish" : "Next"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
