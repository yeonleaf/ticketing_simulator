import { useState } from "react";
import LockBenchmark from "./LockBenchmark";
import TroubleshootingLog from "./TroubleshootingLog";
import VusScalingChart from "./VusScalingChart";

const tabs = [
  { id: "benchmark", label: "Lock Benchmark", component: LockBenchmark },
  { id: "scaling", label: "VUS Scaling", component: VusScalingChart },
  { id: "troubleshooting", label: "Troubleshooting", component: TroubleshootingLog },
];

export default function App() {
  const [active, setActive] = useState("benchmark");
  const ActiveComponent = tabs.find((t) => t.id === active).component;

  return (
    <div style={{ background: "#0B0F1A", minHeight: "100vh" }}>
      <nav
        style={{
          position: "sticky",
          top: 0,
          zIndex: 10,
          background: "#111827",
          borderBottom: "1px solid #1E293B",
          display: "flex",
          justifyContent: "center",
          gap: 4,
          padding: "8px 16px",
        }}
      >
        {tabs.map((t) => (
          <button
            key={t.id}
            onClick={() => setActive(t.id)}
            style={{
              padding: "8px 20px",
              borderRadius: 20,
              border: active === t.id ? "1px solid #00C9A7" : "1px solid transparent",
              background: active === t.id ? "#00C9A718" : "transparent",
              color: active === t.id ? "#00C9A7" : "#94A3B8",
              fontSize: 13,
              fontWeight: 500,
              cursor: "pointer",
              fontFamily: "'JetBrains Mono', monospace",
              transition: "all 0.2s",
            }}
          >
            {t.label}
          </button>
        ))}
      </nav>
      <ActiveComponent />
    </div>
  );
}