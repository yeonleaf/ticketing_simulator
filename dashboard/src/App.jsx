import { useState } from "react";
import {
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
} from "recharts";

const COLORS = {
  pessimistic: "#E8453C",
  optimistic: "#2D8CFF",
  redisson: "#00C9A7",
  bg: "#0B0F1A",
  card: "#111827",
  cardBorder: "#1E293B",
  text: "#E2E8F0",
  textMuted: "#94A3B8",
  accent: "#00C9A7",
};

const rawData = {
  musical: {
    platform: {
      pessimistic: { tps: 101, p90: 4071, p95: 5126, avg: 1197, dup: 1349 },
      optimistic: { tps: 158, p90: 1322, p95: 1478, avg: 458, dup: 855 },
      redisson: { tps: 145, p90: 1503, p95: 1675, avg: 342, dup: 1414 },
    },
    virtual: {
      pessimistic: { tps: 72, p90: 5239, p95: 5918, avg: 2100, dup: 1194 },
      optimistic: { tps: 116, p90: 2697, p95: 2955, avg: 894, dup: 1246 },
      redisson: { tps: 127, p90: 2252, p95: 2403, avg: 615, dup: 1399 },
    },
  },
  uniform: {
    platform: {
      pessimistic: { tps: 166, p90: 1204, p95: 1358, avg: 372, dup: 727 },
      optimistic: { tps: 171, p90: 903, p95: 978, avg: 306, dup: 532 },
      redisson: { tps: 154, p90: 734, p95: 852, avg: 172, dup: 1388 },
    },
    virtual: {
      pessimistic: { tps: 143, p90: 1727, p95: 1815, avg: 656, dup: 834 },
      optimistic: { tps: 155, p90: 1384, p95: 1511, avg: 526, dup: 814 },
      redisson: { tps: 136, p90: 1743, p95: 2037, avg: 444, dup: 1383 },
    },
  },
};

function buildBarData(weight, thread) {
  const d = rawData[weight][thread];
  return [
    { name: "Pessimistic", ...d.pessimistic, fill: COLORS.pessimistic },
    { name: "Optimistic", ...d.optimistic, fill: COLORS.optimistic },
    { name: "Redisson", ...d.redisson, fill: COLORS.redisson },
  ];
}

function buildLatencyBreakdown(weight, thread) {
  const d = rawData[weight][thread];
  return ["Pessimistic", "Optimistic", "Redisson"].map((name) => {
    const key = name.toLowerCase();
    const v = d[key];
    return { name, avg: v.avg, p90: v.p90, p95: v.p95 };
  });
}

function buildRadarData() {
  const d = rawData.musical.virtual;
  const maxTps = Math.max(d.pessimistic.tps, d.optimistic.tps, d.redisson.tps);
  const maxAvg = Math.max(d.pessimistic.avg, d.optimistic.avg, d.redisson.avg);
  const maxP95 = Math.max(d.pessimistic.p95, d.optimistic.p95, d.redisson.p95);
  const maxDup = Math.max(d.pessimistic.dup, d.optimistic.dup, d.redisson.dup);

  const norm = (val, max, invert = false) => {
    const n = (val / max) * 100;
    return invert ? 100 - n + 10 : n;
  };

  return [
    {
      metric: "TPS",
      Pessimistic: norm(d.pessimistic.tps, maxTps),
      Optimistic: norm(d.optimistic.tps, maxTps),
      Redisson: norm(d.redisson.tps, maxTps),
    },
    {
      metric: "응답시간",
      Pessimistic: norm(d.pessimistic.avg, maxAvg, true),
      Optimistic: norm(d.optimistic.avg, maxAvg, true),
      Redisson: norm(d.redisson.avg, maxAvg, true),
    },
    {
      metric: "P95",
      Pessimistic: norm(d.pessimistic.p95, maxP95, true),
      Optimistic: norm(d.optimistic.p95, maxP95, true),
      Redisson: norm(d.redisson.p95, maxP95, true),
    },
    {
      metric: "충돌 효율",
      Pessimistic: norm(d.pessimistic.dup, maxDup, true),
      Optimistic: norm(d.optimistic.dup, maxDup, true),
      Redisson: norm(d.redisson.dup, maxDup, true),
    },
  ];
}

const CustomTooltip = ({ active, payload, label, suffix = "" }) => {
  if (!active || !payload) return null;
  return (
    <div
      style={{
        background: "#1E293B",
        border: "1px solid #334155",
        borderRadius: 8,
        padding: "10px 14px",
        fontSize: 13,
      }}
    >
      <p style={{ color: "#E2E8F0", fontWeight: 600, marginBottom: 6 }}>
        {label}
      </p>
      {payload.map((p, i) => (
        <p key={i} style={{ color: p.color || p.fill, margin: "3px 0" }}>
          {p.name}: <b>{p.value.toLocaleString()}{suffix}</b>
        </p>
      ))}
    </div>
  );
};

const Badge = ({ children, active, onClick }) => (
  <button
    onClick={onClick}
    style={{
      padding: "6px 16px",
      borderRadius: 20,
      border: active ? `1px solid ${COLORS.accent}` : "1px solid #334155",
      background: active ? `${COLORS.accent}18` : "transparent",
      color: active ? COLORS.accent : COLORS.textMuted,
      fontSize: 13,
      fontWeight: 500,
      cursor: "pointer",
      transition: "all 0.2s",
      fontFamily: "'JetBrains Mono', monospace",
    }}
  >
    {children}
  </button>
);

const SectionTitle = ({ children, sub }) => (
  <div style={{ marginBottom: 16 }}>
    <h2
      style={{
        fontSize: 18,
        fontWeight: 700,
        color: COLORS.text,
        margin: 0,
        letterSpacing: "-0.02em",
      }}
    >
      {children}
    </h2>
    {sub && (
      <p style={{ fontSize: 13, color: COLORS.textMuted, margin: "4px 0 0" }}>
        {sub}
      </p>
    )}
  </div>
);

const Card = ({ children, style = {} }) => (
  <div
    style={{
      background: COLORS.card,
      border: `1px solid ${COLORS.cardBorder}`,
      borderRadius: 12,
      padding: 24,
      ...style,
    }}
  >
    {children}
  </div>
);

const InsightCard = ({ icon, title, body, color }) => (
  <div
    style={{
      background: `${color}08`,
      border: `1px solid ${color}25`,
      borderRadius: 10,
      padding: "16px 18px",
      flex: 1,
      minWidth: 220,
    }}
  >
    <div style={{ fontSize: 20, marginBottom: 6 }}>{icon}</div>
    <div
      style={{
        fontSize: 13,
        fontWeight: 700,
        color,
        marginBottom: 4,
        fontFamily: "'JetBrains Mono', monospace",
      }}
    >
      {title}
    </div>
    <div style={{ fontSize: 12, color: COLORS.textMuted, lineHeight: 1.5 }}>
      {body}
    </div>
  </div>
);

export default function Dashboard() {
  const [weight, setWeight] = useState("musical");
  const [thread, setThread] = useState("virtual");

  const barData = buildBarData(weight, thread);
  const latencyData = buildLatencyBreakdown(weight, thread);
  const radarData = buildRadarData();

  const weightLabel = weight === "musical" ? "Musical Standard" : "Uniform";
  const threadLabel = thread === "platform" ? "Platform Thread" : "Virtual Thread";

  return (
    <div
      style={{
        background: COLORS.bg,
        minHeight: "100vh",
        color: COLORS.text,
        fontFamily:
          "'Pretendard', -apple-system, BlinkMacSystemFont, sans-serif",
        padding: "32px 24px",
      }}
    >
      <link
        href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap"
        rel="stylesheet"
      />

      {/* Header */}
      <div style={{ maxWidth: 960, margin: "0 auto 32px" }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginBottom: 4 }}>
          <h1
            style={{
              fontSize: 28,
              fontWeight: 800,
              margin: 0,
              color: COLORS.text,
              letterSpacing: "-0.03em",
              fontFamily: "'JetBrains Mono', monospace",
            }}
          >
            Lock Strategy Benchmark
          </h1>
          <span style={{ fontSize: 13, color: COLORS.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>
            VUS 300 · Caching Enabled
          </span>
        </div>
        <p style={{ fontSize: 14, color: COLORS.textMuted, margin: "8px 0 0" }}>
          뮤지컬 티켓팅 시뮬레이터 — Pessimistic vs Optimistic vs Redisson 동시성 제어 전략 비교
        </p>
      </div>

      <div style={{ maxWidth: 960, margin: "0 auto" }}>
        {/* Controls */}
        <div
          style={{
            display: "flex",
            gap: 24,
            marginBottom: 28,
            flexWrap: "wrap",
            alignItems: "center",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontSize: 12, color: COLORS.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>
              WEIGHT
            </span>
            <Badge active={weight === "musical"} onClick={() => setWeight("musical")}>
              Musical
            </Badge>
            <Badge active={weight === "uniform"} onClick={() => setWeight("uniform")}>
              Uniform
            </Badge>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontSize: 12, color: COLORS.textMuted, fontFamily: "'JetBrains Mono', monospace" }}>
              THREAD
            </span>
            <Badge active={thread === "platform"} onClick={() => setThread("platform")}>
              Platform
            </Badge>
            <Badge active={thread === "virtual"} onClick={() => setThread("virtual")}>
              Virtual
            </Badge>
          </div>
        </div>

        {/* KPI Row */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: 16,
            marginBottom: 28,
          }}
        >
          {barData.map((d) => {
            const isWinner = d.tps === Math.max(...barData.map((b) => b.tps));
            return (
              <Card
                key={d.name}
                style={{
                  borderColor: isWinner ? `${d.fill}50` : COLORS.cardBorder,
                  position: "relative",
                  overflow: "hidden",
                }}
              >
                {isWinner && (
                  <div
                    style={{
                      position: "absolute",
                      top: 10,
                      right: 12,
                      fontSize: 10,
                      fontWeight: 700,
                      color: d.fill,
                      background: `${d.fill}15`,
                      padding: "2px 8px",
                      borderRadius: 10,
                      fontFamily: "'JetBrains Mono', monospace",
                    }}
                  >
                    BEST TPS
                  </div>
                )}
                <div
                  style={{
                    fontSize: 11,
                    color: d.fill,
                    fontWeight: 700,
                    marginBottom: 8,
                    fontFamily: "'JetBrains Mono', monospace",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}
                >
                  {d.name}
                </div>
                <div
                  style={{
                    fontSize: 36,
                    fontWeight: 800,
                    color: COLORS.text,
                    fontFamily: "'JetBrains Mono', monospace",
                    lineHeight: 1,
                    marginBottom: 4,
                  }}
                >
                  {d.tps}
                  <span style={{ fontSize: 14, color: COLORS.textMuted, fontWeight: 400 }}>
                    {" "}TPS
                  </span>
                </div>
                <div style={{ fontSize: 12, color: COLORS.textMuted, marginTop: 10 }}>
                  avg {d.avg.toLocaleString()}ms · p95 {d.p95.toLocaleString()}ms
                </div>
              </Card>
            );
          })}
        </div>

        {/* Charts Row 1 */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 16 }}>
          {/* TPS */}
          <Card>
            <SectionTitle sub={`${weightLabel} · ${threadLabel}`}>
              Total TPS
            </SectionTitle>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={barData} barCategoryGap="30%" margin={{ left: 10, right: 10 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
                <XAxis dataKey="name" tick={{ fill: COLORS.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} padding={{ left: 20, right: 20 }} />
                <YAxis tick={{ fill: COLORS.textMuted, fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={<CustomTooltip />} />
                <Bar dataKey="tps" name="TPS" radius={[4, 4, 0, 0]}>
                  {barData.map((d, i) => (
                    <Cell key={i} fill={d.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Card>

          {/* Latency Breakdown */}
          <Card>
            <SectionTitle sub="avg vs p(90) vs p(95)">
              응답시간 분포 (ms)
            </SectionTitle>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={latencyData} barCategoryGap="25%" margin={{ left: 10, right: 10 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
                <XAxis dataKey="name" tick={{ fill: COLORS.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} padding={{ left: 20, right: 20 }} />
                <YAxis tick={{ fill: COLORS.textMuted, fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={<CustomTooltip suffix="ms" />} />
                <Legend
                  wrapperStyle={{ fontSize: 11, color: COLORS.textMuted }}
                  iconType="square"
                  iconSize={8}
                />
                <Bar dataKey="avg" name="avg" fill="#60A5FA" radius={[3, 3, 0, 0]} />
                <Bar dataKey="p90" name="p(90)" fill="#F59E0B" radius={[3, 3, 0, 0]} />
                <Bar dataKey="p95" name="p(95)" fill="#EF4444" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </div>

        {/* Charts Row 2 */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 16 }}>
          {/* Duplicate Hold */}
          <Card>
            <SectionTitle sub={`${weightLabel} · ${threadLabel}`}>
              중복 선점 횟수
            </SectionTitle>
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={barData} barCategoryGap="30%" margin={{ left: 10, right: 10 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
                <XAxis dataKey="name" tick={{ fill: COLORS.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} padding={{ left: 20, right: 20 }} />
                <YAxis tick={{ fill: COLORS.textMuted, fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={<CustomTooltip suffix="건" />} />
                <Bar dataKey="dup" name="중복 선점" radius={[4, 4, 0, 0]}>
                  {barData.map((d, i) => (
                    <Cell key={i} fill={d.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Card>

          {/* Radar — Musical Virtual only */}
          <Card>
            <SectionTitle sub="Musical · Virtual (높을수록 우수)">
              종합 비교 레이더
            </SectionTitle>
            <ResponsiveContainer width="100%" height={240}>
              <RadarChart data={radarData} cx="50%" cy="50%" outerRadius="72%">
                <PolarGrid stroke="#1E293B" />
                <PolarAngleAxis
                  dataKey="metric"
                  tick={{ fill: COLORS.textMuted, fontSize: 12 }}
                />
                <PolarRadiusAxis tick={false} axisLine={false} domain={[0, 110]} />
                <Radar
                  name="Pessimistic"
                  dataKey="Pessimistic"
                  stroke={COLORS.pessimistic}
                  fill={COLORS.pessimistic}
                  fillOpacity={0.12}
                  strokeWidth={2}
                />
                <Radar
                  name="Optimistic"
                  dataKey="Optimistic"
                  stroke={COLORS.optimistic}
                  fill={COLORS.optimistic}
                  fillOpacity={0.12}
                  strokeWidth={2}
                />
                <Radar
                  name="Redisson"
                  dataKey="Redisson"
                  stroke={COLORS.redisson}
                  fill={COLORS.redisson}
                  fillOpacity={0.12}
                  strokeWidth={2}
                />
                <Legend
                  wrapperStyle={{ fontSize: 11, color: COLORS.textMuted }}
                  iconType="square"
                  iconSize={8}
                />
              </RadarChart>
            </ResponsiveContainer>
          </Card>
        </div>

        {/* Insights */}
        <Card style={{ marginBottom: 28 }}>
          <SectionTitle>Key Insights</SectionTitle>
          <div style={{ display: "flex", gap: 14, flexWrap: "wrap" }}>
            <InsightCard
              icon="🏆"
              title="Redisson — Hotspot 최강"
              body="Musical+Virtual에서 TPS 127, 평균 615ms, p95 2403ms로 세 지표 모두 1등. Hotspot 환경에서 가장 안정적."
              color={COLORS.redisson}
            />
            <InsightCard
              icon="⚡"
              title="Optimistic — 균등 분산 시 최고 TPS"
              body="Uniform Platform TPS 171로 최고이나, Musical Virtual에서 p95가 2955ms로 급등. Hotspot에 취약."
              color={COLORS.optimistic}
            />
            <InsightCard
              icon="🐢"
              title="Pessimistic — Tail Latency 심각"
              body="Musical Virtual p95 5918ms. 20명 중 1명이 6초 대기. 동시성 높은 서비스에 부적합."
              color={COLORS.pessimistic}
            />
          </div>
        </Card>

        {/* Footer */}
        <div style={{ textAlign: "center", padding: "16px 0", fontSize: 11, color: "#475569", fontFamily: "'JetBrains Mono', monospace" }}>
          Musical Ticketing Simulator · Java 21 Virtual Threads · Spring Boot · MySQL · Redis · k6 Load Test
        </div>
      </div>
    </div>
  );
}
