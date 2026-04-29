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
  ReferenceLine,
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
};

const tpsData = [
  { vus: 300, Pessimistic: 101, Optimistic: 158, Redisson: 145 },
  { vus: 500, Pessimistic: 88, Optimistic: 193, Redisson: 211 },
  { vus: 700, Pessimistic: 198, Optimistic: 212, Redisson: 269 },
  { vus: 1000, Pessimistic: 185, Optimistic: 238, Redisson: 220 },
];

const avgData = [
  { vus: 300, Pessimistic: 1197, Optimistic: 458, Redisson: 342 },
  { vus: 500, Pessimistic: 3706, Optimistic: 987, Redisson: 934 },
  { vus: 700, Pessimistic: 767, Optimistic: 628, Redisson: 385 },
  { vus: 1000, Pessimistic: 336, Optimistic: 646, Redisson: 195 },
];

const p95Data = [
  { vus: 300, Pessimistic: 5126, Optimistic: 1478, Redisson: 1675 },
  { vus: 500, Pessimistic: 8780, Optimistic: 3270, Redisson: 3250 },
  { vus: 700, Pessimistic: 3248, Optimistic: 2429, Redisson: 1551 },
  { vus: 1000, Pessimistic: 2515, Optimistic: 1056, Redisson: 1354 },
];

const dupData = [
  { vus: 300, Pessimistic: 1349, Optimistic: 855, Redisson: 1414 },
  { vus: 500, Pessimistic: 1163, Optimistic: 1382, Redisson: 3615 },
  { vus: 700, Pessimistic: 1711, Optimistic: 1124, Redisson: 3549 },
  { vus: 1000, Pessimistic: 713, Optimistic: 1727, Redisson: 2369 },
];

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

const renderChart = (data, suffix, yDomain) => (
  <ResponsiveContainer width="100%" height={260}>
    <LineChart data={data} margin={{ left: 10, right: 20, top: 10, bottom: 5 }}>
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
  return (
    <div
      style={{
        background: COLORS.bg,
        minHeight: "100vh",
        fontFamily: "'Pretendard', -apple-system, sans-serif",
        padding: "32px 24px",
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
            {renderChart(tpsData, "", [0, 300])}
          </Card>

          <Card>
            <ChartTitle title="평균 응답시간 (ms)" sub="낮을수록 우수" />
            {renderChart(avgData, "ms", [0, "auto"])}
          </Card>

          <Card>
            <ChartTitle title="P95 응답시간 (ms)" sub="낮을수록 우수 · tail latency" />
            {renderChart(p95Data, "ms", [0, "auto"])}
          </Card>

          <Card>
            <ChartTitle title="중복 선점" sub="낮을수록 우수" />
            {renderChart(dupData, "건", [0, "auto"])}
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
            <div
              style={{
                flex: 1,
                minWidth: 200,
                background: `${COLORS.optimistic}08`,
                border: `1px solid ${COLORS.optimistic}25`,
                borderRadius: 10,
                padding: "14px 16px",
              }}
            >
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  color: COLORS.optimistic,
                  marginBottom: 6,
                  fontFamily: "'JetBrains Mono', monospace",
                }}
              >
                Optimistic — TPS 선형 증가
              </div>
              <div
                style={{ fontSize: 12, color: COLORS.textMuted, lineHeight: 1.6 }}
              >
                158→238로 꾸준히 상승. 단일 인스턴스에서 최고 처리량이나 멀티
                인스턴스 시 DB 락 무력화
              </div>
            </div>

            <div
              style={{
                flex: 1,
                minWidth: 200,
                background: `${COLORS.redisson}08`,
                border: `1px solid ${COLORS.redisson}25`,
                borderRadius: 10,
                padding: "14px 16px",
              }}
            >
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  color: COLORS.redisson,
                  marginBottom: 6,
                  fontFamily: "'JetBrains Mono', monospace",
                }}
              >
                Redisson — 응답시간 최안정
              </div>
              <div
                style={{ fontSize: 12, color: COLORS.textMuted, lineHeight: 1.6 }}
              >
                1000 VUS에서도 avg 195ms로 최저. 700에서 TPS 피크(269) 후
                하락하는 degradation curve 확인
              </div>
            </div>

            <div
              style={{
                flex: 1,
                minWidth: 200,
                background: `${COLORS.pessimistic}08`,
                border: `1px solid ${COLORS.pessimistic}25`,
                borderRadius: 10,
                padding: "14px 16px",
              }}
            >
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  color: COLORS.pessimistic,
                  marginBottom: 6,
                  fontFamily: "'JetBrains Mono', monospace",
                }}
              >
                Pessimistic — 500 VUS 병목
              </div>
              <div
                style={{ fontSize: 12, color: COLORS.textMuted, lineHeight: 1.6 }}
              >
                500 VUS에서 p95 8.8초로 폭발. 좌석 잔여 + 극심한 lock 경합이
                겹치는 최악의 구간
              </div>
            </div>
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
