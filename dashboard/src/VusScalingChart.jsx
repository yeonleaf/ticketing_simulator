import { useState } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
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

const data = {
  1: {
    tps: [
      { vus: 300, Pessimistic: 101, Optimistic: 158, Redisson: 145 },
      { vus: 500, Pessimistic: 88, Optimistic: 193, Redisson: 211 },
      { vus: 700, Pessimistic: 198, Optimistic: 212, Redisson: 269 },
      { vus: 1000, Pessimistic: 185, Optimistic: 238, Redisson: 220 },
    ],
    avg: [
      { vus: 300, Pessimistic: 1197, Optimistic: 458, Redisson: 342 },
      { vus: 500, Pessimistic: 3706, Optimistic: 987, Redisson: 934 },
      { vus: 700, Pessimistic: 767, Optimistic: 628, Redisson: 385 },
      { vus: 1000, Pessimistic: 336, Optimistic: 646, Redisson: 195 },
    ],
    p95: [
      { vus: 300, Pessimistic: 5126, Optimistic: 1478, Redisson: 1675 },
      { vus: 500, Pessimistic: 8780, Optimistic: 3270, Redisson: 3250 },
      { vus: 700, Pessimistic: 3248, Optimistic: 2429, Redisson: 1551 },
      { vus: 1000, Pessimistic: 2515, Optimistic: 1056, Redisson: 1354 },
    ],
    dup: [
      { vus: 300, Pessimistic: 1349, Optimistic: 855, Redisson: 1414 },
      { vus: 500, Pessimistic: 1163, Optimistic: 1382, Redisson: 3615 },
      { vus: 700, Pessimistic: 1711, Optimistic: 1124, Redisson: 3549 },
      { vus: 1000, Pessimistic: 713, Optimistic: 1727, Redisson: 2369 },
    ],
    insights: [
      {
        color: COLORS.optimistic,
        title: "Optimistic — TPS 선형 증가",
        body: "158→238로 꾸준히 상승. 단일 인스턴스에서 최고 처리량이나 멀티 인스턴스 시 DB 락 무력화",
      },
      {
        color: COLORS.redisson,
        title: "Redisson — 응답시간 최안정",
        body: "1000 VUS에서도 avg 195ms로 최저. 700에서 TPS 피크(269) 후 하락하는 degradation curve 확인",
      },
      {
        color: COLORS.pessimistic,
        title: "Pessimistic — 500 VUS 병목",
        body: "500 VUS에서 p95 8.8초로 폭발. 좌석 잔여 + 극심한 lock 경합이 겹치는 최악의 구간",
      },
    ],
  },
  2: {
    tps: [
      { vus: 300, Pessimistic: 215, Optimistic: 217, Redisson: 284 },
      { vus: 500, Pessimistic: 343, Optimistic: 316, Redisson: 387 },
      { vus: 700, Pessimistic: 320, Optimistic: 216, Redisson: 332 },
      { vus: 1000, Pessimistic: 257, Optimistic: 308, Redisson: 538 },
    ],
    avg: [
      { vus: 300, Pessimistic: 158, Optimistic: 75, Redisson: 55 },
      { vus: 500, Pessimistic: 218, Optimistic: 197, Redisson: 310 },
      { vus: 700, Pessimistic: 137, Optimistic: 1435, Redisson: 679 },
      { vus: 1000, Pessimistic: 1084, Optimistic: 214, Redisson: 338 },
    ],
    p95: [
      { vus: 300, Pessimistic: 486, Optimistic: 222, Redisson: 321 },
      { vus: 500, Pessimistic: 683, Optimistic: 705, Redisson: 2052 },
      { vus: 700, Pessimistic: 318, Optimistic: 6574, Redisson: 2113 },
      { vus: 1000, Pessimistic: 3346, Optimistic: 657, Redisson: 1010 },
    ],
    dup: [
      { vus: 300, Pessimistic: 622, Optimistic: 540, Redisson: 2417 },
      { vus: 500, Pessimistic: 1317, Optimistic: 1139, Redisson: 4743 },
      { vus: 700, Pessimistic: 1229, Optimistic: 2778, Redisson: 5929 },
      { vus: 1000, Pessimistic: 2905, Optimistic: 1988, Redisson: 9266 },
    ],
    insights: [
      {
        color: COLORS.redisson,
        title: "Redisson — TPS 538, 2.4배 스케일링",
        body: "1000 VUS에서 단일 인스턴스(220) 대비 2.4배. Pessimistic(257), Optimistic(308)을 압도",
      },
      {
        color: COLORS.optimistic,
        title: "Optimistic — 700 VUS에서 붕괴",
        body: "p95 6574ms로 폭발, TPS 216으로 급락. 2인스턴스 간 version 충돌로 재시도 폭증",
      },
      {
        color: COLORS.pessimistic,
        title: "Pessimistic — 안정적이나 천장 낮음",
        body: "TPS 215~343 범위로 완만. DB 행 잠금이 직렬화하여 인스턴스 추가 효과 제한적",
      },
    ],
  },
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

const CustomTooltip = ({ active, payload, label, suffix = "" }) => {
  if (!active || !payload) return null;
  return (
    <div
      style={{
        background: "#1E293B",
        border: "1px solid #334155",
        borderRadius: 8,
        padding: "12px 16px",
        fontSize: 13,
      }}
    >
      <p
        style={{
          color: COLORS.text,
          fontWeight: 600,
          marginBottom: 8,
          fontFamily: "'JetBrains Mono', monospace",
        }}
      >
        VUS {label}
      </p>
      {payload.map((p, i) => (
        <p key={i} style={{ color: p.color, margin: "4px 0" }}>
          {p.name}:{" "}
          <b>
            {p.value.toLocaleString()}
            {suffix}
          </b>
        </p>
      ))}
    </div>
  );
};

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

const ChartTitle = ({ title, sub }) => (
  <div style={{ marginBottom: 16 }}>
    <h2
      style={{
        fontSize: 16,
        fontWeight: 700,
        color: COLORS.text,
        margin: 0,
      }}
    >
      {title}
    </h2>
    {sub && (
      <p style={{ fontSize: 12, color: COLORS.textMuted, margin: "4px 0 0" }}>
        {sub}
      </p>
    )}
  </div>
);

const renderChart = (chartData, suffix, yDomain) => (
  <ResponsiveContainer width="100%" height={260}>
    <LineChart data={chartData} margin={{ left: 10, right: 20, top: 10, bottom: 5 }}>
      <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
      <XAxis
        dataKey="vus"
        tick={{ fill: COLORS.textMuted, fontSize: 12 }}
        axisLine={false}
        tickLine={false}
        label={{
          value: "VUS",
          position: "insideBottomRight",
          offset: -5,
          fill: COLORS.textMuted,
          fontSize: 11,
        }}
      />
      <YAxis
        tick={{ fill: COLORS.textMuted, fontSize: 11 }}
        axisLine={false}
        tickLine={false}
        domain={yDomain}
      />
      <Tooltip content={<CustomTooltip suffix={suffix} />} />
      <Legend
        wrapperStyle={{ fontSize: 12, color: COLORS.textMuted }}
        iconType="circle"
        iconSize={8}
      />
      <Line
        type="monotone"
        dataKey="Pessimistic"
        stroke={COLORS.pessimistic}
        strokeWidth={2.5}
        dot={{ r: 5, fill: COLORS.pessimistic, strokeWidth: 0 }}
        activeDot={{ r: 7 }}
      />
      <Line
        type="monotone"
        dataKey="Optimistic"
        stroke={COLORS.optimistic}
        strokeWidth={2.5}
        dot={{ r: 5, fill: COLORS.optimistic, strokeWidth: 0 }}
        activeDot={{ r: 7 }}
      />
      <Line
        type="monotone"
        dataKey="Redisson"
        stroke={COLORS.redisson}
        strokeWidth={2.5}
        dot={{ r: 5, fill: COLORS.redisson, strokeWidth: 0 }}
        activeDot={{ r: 7 }}
      />
    </LineChart>
  </ResponsiveContainer>
);

export default function ScalingChart() {
  const [instances, setInstances] = useState(1);
  const d = data[instances];

  return (
    <div
      style={{
        background: COLORS.bg,
        minHeight: "100vh",
        fontFamily: "'Pretendard', -apple-system, sans-serif",
        padding: "32px 24px",
        textAlign: "left",
      }}
    >
      <link
        href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap"
        rel="stylesheet"
      />

      <div style={{ maxWidth: 960, margin: "0 auto" }}>
        {/* Header */}
        <div style={{ marginBottom: 32 }}>
          <div
            style={{
              display: "flex",
              alignItems: "baseline",
              gap: 12,
              marginBottom: 4,
            }}
          >
            <h1
              style={{
                fontSize: 26,
                fontWeight: 800,
                color: COLORS.text,
                margin: 0,
                letterSpacing: "-0.03em",
                fontFamily: "'JetBrains Mono', monospace",
              }}
            >
              VUS Scaling Test
            </h1>
            <span
              style={{
                fontSize: 13,
                color: COLORS.textMuted,
                fontFamily: "'JetBrains Mono', monospace",
              }}
            >
              300 → 500 → 700 → 1000
            </span>
          </div>
          <p style={{ fontSize: 14, color: COLORS.textMuted, margin: "8px 0 0" }}>
            Musical Standard · Platform Thread · Caching Enabled
          </p>
        </div>

        {/* Instance Toggle */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            marginBottom: 24,
          }}
        >
          <span
            style={{
              fontSize: 12,
              color: COLORS.textMuted,
              fontFamily: "'JetBrains Mono', monospace",
            }}
          >
            INSTANCES
          </span>
          <Badge active={instances === 1} onClick={() => setInstances(1)}>
            1 Instance
          </Badge>
          <Badge active={instances === 2} onClick={() => setInstances(2)}>
            2 Instances
          </Badge>
        </div>

        {/* Charts Grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: 16,
            marginBottom: 16,
          }}
        >
          <Card>
            <ChartTitle title="Total TPS" sub="높을수록 우수" />
            {renderChart(d.tps, "", [0, instances === 2 ? 600 : 300])}
          </Card>

          <Card>
            <ChartTitle title="평균 응답시간 (ms)" sub="낮을수록 우수" />
            {renderChart(d.avg, "ms", [0, "auto"])}
          </Card>

          <Card>
            <ChartTitle title="P95 응답시간 (ms)" sub="낮을수록 우수 · tail latency" />
            {renderChart(d.p95, "ms", [0, "auto"])}
          </Card>

          <Card>
            <ChartTitle title="중복 선점" sub="낮을수록 우수" />
            {renderChart(d.dup, "건", [0, "auto"])}
          </Card>
        </div>

        {/* Insight Cards */}
        <Card>
          <h2
            style={{
              fontSize: 16,
              fontWeight: 700,
              color: COLORS.text,
              margin: "0 0 14px",
            }}
          >
            Key Insights
          </h2>
          <div style={{ display: "flex", gap: 14, flexWrap: "wrap" }}>
            {d.insights.map((item, i) => (
              <div
                key={i}
                style={{
                  flex: 1,
                  minWidth: 200,
                  background: `${item.color}08`,
                  border: `1px solid ${item.color}25`,
                  borderRadius: 10,
                  padding: "14px 16px",
                }}
              >
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: 700,
                    color: item.color,
                    marginBottom: 6,
                    fontFamily: "'JetBrains Mono', monospace",
                  }}
                >
                  {item.title}
                </div>
                <div
                  style={{
                    fontSize: 12,
                    color: COLORS.textMuted,
                    lineHeight: 1.6,
                  }}
                >
                  {item.body}
                </div>
              </div>
            ))}
          </div>
        </Card>

        {/* Footer */}
        <div
          style={{
            textAlign: "center",
            padding: "20px 0",
            fontSize: 11,
            color: "#475569",
            fontFamily: "'JetBrains Mono', monospace",
          }}
        >
          Musical Ticketing Simulator · Java 21 Virtual Threads · Spring Boot ·
          MySQL · Redis · k6
        </div>
      </div>
    </div>
  );
}