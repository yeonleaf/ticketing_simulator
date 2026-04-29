import { useState } from "react";

const problems = [
  {
    id: 1,
    tag: "Transaction",
    title: "UnexpectedRollbackException 발생",
    symptom:
      "@Transactional 메서드 내부에서 예외를 catch했음에도 커밋 시점에 UnexpectedRollbackException이 발생하여 전체 트랜잭션이 롤백됨",
    cause:
      "@Transactional은 예외 발생 시 내부적으로 rollback-only를 마킹함. catch로 예외를 삼켜도 마킹은 해제되지 않아, 외부 트랜잭션이 커밋을 시도하면 Spring이 UnexpectedRollbackException을 던짐",
    solution:
      "TransactionTemplate으로 전환하여 트랜잭션 경계를 명시적으로 제어. 예외 발생 시 TransactionTemplate이 자동으로 rollback 후 re-throw하므로, 트랜잭션 밖에서 예외를 catch하여 안전하게 처리",
    code: `// Before: @Transactional 내부 catch → rollback-only 충돌
@Transactional
public SeatHoldResult hold(...) {
    try {
        seat.hold(audienceId);  // 예외 발생 시 rollback-only 마킹
    } catch (Exception e) {
        return SeatHoldResult.FAIL;  // catch해도 마킹 해제 안 됨
    }
}

// After: TransactionTemplate으로 경계 밖에서 예외 처리
public SeatHoldResult hold(...) {
    try {
        return transactionTemplate.execute(status -> {
            seat.hold(audienceId);
            return SeatHoldResult.SUCCESS;
        });
    } catch (Exception e) {
        return SeatHoldResult.FAIL;  // 트랜잭션 이미 rollback 완료
    }
}`,
  },
  {
    id: 2,
    tag: "Spring AOP",
    title: "Self-invocation으로 @Transactional 미적용",
    symptom:
      "같은 클래스 내에서 hold() → doHold() 호출 시 doHold()의 @Transactional(REQUIRES_NEW)이 동작하지 않아 트랜잭션 분리가 되지 않음",
    cause:
      "Spring AOP는 프록시 기반으로 동작하므로, 같은 클래스 내부의 메서드 호출(self-invocation)은 프록시를 거치지 않음. 따라서 @Transactional 어노테이션이 무시됨",
    solution:
      "doHold()를 별도의 InternalService 클래스로 분리하여, 외부 빈 호출을 통해 Spring 프록시가 정상적으로 개입하도록 구조 변경",
    code: `// Before: self-invocation → 프록시 우회
public class SeatService {
    public SeatHoldResult hold(...) {
        return doHold(...);  // this.doHold() → 프록시 미경유
    }

    @Transactional(propagation = REQUIRES_NEW)
    public SeatHoldResult doHold(...) { ... }  // 적용 안 됨
}

// After: 별도 빈으로 분리 → 프록시 정상 경유
public class SeatService {
    private final SeatInternalService internalService;

    public SeatHoldResult hold(...) {
        return internalService.doHold(...);  // 프록시 경유
    }
}

@Service
public class SeatInternalService {
    @Transactional(propagation = REQUIRES_NEW)
    public SeatHoldResult doHold(...) { ... }  // 정상 적용
}`,
  },
  {
    id: 3,
    tag: "Concurrency",
    title: "Lock wait timeout exceeded (MySQL 1205)",
    symptom:
      "Optimistic Lock 전략에서 300 VU 부하 테스트 시 Lock wait timeout exceeded 에러가 다수 발생하며 트랜잭션 실패율 증가",
    cause:
      "Redis 캐시 갱신 로직이 TransactionTemplate 내부에 위치하여, DB row lock을 보유한 상태에서 Redis 네트워크 I/O(읽기 + 역직렬화 + 필터링 + 쓰기)가 수행됨. 이로 인해 lock 보유 시간이 길어져 대기 중인 다른 스레드들이 innodb_lock_wait_timeout을 초과",
    solution:
      "캐시 갱신 로직을 트랜잭션 경계 밖으로 분리하여 DB lock 보유 시간을 최소화. DB 작업 완료 → 커밋 → lock 해제 후에 캐시를 업데이트하는 구조로 변경",
    code: `// Before: 트랜잭션 안에서 Redis I/O → lock 보유 시간 증가
transactionTemplate.execute(status -> {
    seat.hold(audienceId);
    seatRepository.saveAndFlush(seat);
    // ❌ DB lock 보유 중에 Redis I/O 발생
    redisTemplate.opsForValue().get(key);
    redisTemplate.opsForValue().set(key, updated);
    return SeatHoldResult.SUCCESS;
});

// After: 트랜잭션 밖으로 캐시 분리 → lock 즉시 해제
var result = transactionTemplate.execute(status -> {
    seat.hold(audienceId);
    seatRepository.saveAndFlush(seat);
    return SeatHoldResult.SUCCESS;  // 커밋 → lock 해제
});
if (result == SeatHoldResult.SUCCESS) {
    evictSeatFromCache(seatId, simulationId);  // lock 해제 후 캐시 갱신
}`,
  },
  {
    id: 4,
    tag: "Architecture",
    title: "캐시 분리 후 Redisson TPS 57% 악화 (160→69)",
    symptom:
      "best practice에 따라 캐시 로직을 트랜잭션 밖 + 분산 락 밖으로 분리했더니, Redisson 전략의 TPS가 160에서 69로 57% 하락하고 평균 응답시간이 265ms에서 2369ms로 9배 악화",
    cause:
      "분산 락 해제 ~ 캐시 갱신 사이의 시간 공백 동안 다른 스레드들이 stale 캐시를 읽어 이미 선점된 좌석을 시도함. DB에서 ALREADY_HELD로 반환되지만 불필요한 DB 접근 사이클이 반복되어 전체 처리량 감소",
    solution:
      "Redisson 전략에서는 캐시 갱신을 트랜잭션 밖 + 분산 락 안에 유지. 분산 락이 이미 동시 접근을 직렬화하고 있으므로 캐시가 락 안에 있어도 DB lock 경합이 발생하지 않음. 다음 스레드가 락을 획득했을 때 갱신된 캐시를 읽을 수 있어 불필요한 DB 접근 방지",
    code: `// 최종 구조: 트랜잭션 밖, 분산 락 안
RLock lock = redissonClient.getLock("seat:lock:" + seatId);
lock.tryLock(5, 30, TimeUnit.SECONDS);
try {
    // 1. DB 트랜잭션 (짧게)
    var result = internalService.doHold(seatId, audienceId);
    
    // 2. 캐시 갱신 (트랜잭션 밖, 락 안)
    if (result == SeatHoldResult.SUCCESS) {
        evictSeatFromCache(seatId, simulationId);
    }
} finally {
    lock.unlock();  // 캐시까지 갱신 후 락 해제
}`,
  },
  {
    id: 5,
    tag: "Performance",
    title: "실패 시에도 캐시 I/O 발생으로 불필요한 지연",
    symptom:
      "캐시 분리 후 SUCCESS 가드를 추가했음에도 TPS가 기대만큼 회복되지 않음 (69→103, 원본 160에 미달)",
    cause:
      "doHold()가 ALREADY_HELD나 FAIL을 반환하는 경우에도 캐시 갱신 로직이 실행되어, 결과와 무관하게 매 요청마다 Redis read/write 발생. 300 VU 환경에서 대부분의 요청이 실패하므로 불필요한 Redis I/O가 분산 락 보유 시간을 늘림",
    solution:
      "SeatHoldResult가 SUCCESS일 때만 캐시를 갱신하도록 조건 가드 추가. 실패 시에는 Redis에 접근하지 않아 분산 락 보유 시간 최소화",
    code: `// Before: 결과와 무관하게 항상 캐시 접근
var result = internalService.doHold(seatId, audienceId);
String cached = redisTemplate.opsForValue().get(key);  // ❌ FAIL이어도 실행
// ... 캐시 갱신 로직

// After: SUCCESS일 때만 캐시 갱신
var result = internalService.doHold(seatId, audienceId);
if (result.getSeatHoldResult() == SeatHoldResult.SUCCESS) {
    evictSeatFromCache(seatId, simulationId);  // ✅ 성공 시에만
}`,
  },
];

const tagColors = {
  Transaction: { bg: "#EF444418", text: "#EF4444", border: "#EF444430" },
  "Spring AOP": { bg: "#A855F718", text: "#A855F7", border: "#A855F730" },
  Concurrency: { bg: "#F59E0B18", text: "#F59E0B", border: "#F59E0B30" },
  Architecture: { bg: "#3B82F618", text: "#3B82F6", border: "#3B82F630" },
  Performance: { bg: "#10B98118", text: "#10B981", border: "#10B98130" },
};

export default function TroubleshootingTimeline() {
  const [expanded, setExpanded] = useState(null);

  return (
    <div
      style={{
        background: "#0B0F1A",
        minHeight: "100vh",
        fontFamily: "'Pretendard', -apple-system, sans-serif",
        padding: "40px 24px",
        textAlign: "left",
      }}
    >
      <link
        href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600&display=swap"
        rel="stylesheet"
      />

      <div style={{ maxWidth: 800, margin: "0 auto" }}>
        {/* Header */}
        <div style={{ marginBottom: 40 }}>
          <h1
            style={{
              fontSize: 26,
              fontWeight: 800,
              color: "#E2E8F0",
              margin: 0,
              letterSpacing: "-0.03em",
              fontFamily: "'JetBrains Mono', monospace",
            }}
          >
            Troubleshooting Log
          </h1>
          <p
            style={{
              fontSize: 14,
              color: "#64748B",
              margin: "8px 0 0",
            }}
          >
            뮤지컬 티켓팅 시뮬레이터 — 프로젝트 진행 중 해결한 문제들
          </p>
        </div>

        {/* Timeline */}
        <div style={{ position: "relative" }}>
          {/* Vertical line */}
          <div
            style={{
              position: "absolute",
              left: 15,
              top: 8,
              bottom: 8,
              width: 2,
              background:
                "linear-gradient(to bottom, #1E293B, #334155, #1E293B)",
              borderRadius: 1,
            }}
          />

          {problems.map((p, i) => {
            const isOpen = expanded === p.id;
            const tc = tagColors[p.tag];
            return (
              <div
                key={p.id}
                style={{
                  position: "relative",
                  paddingLeft: 48,
                  marginBottom: i < problems.length - 1 ? 24 : 0,
                }}
              >
                {/* Dot */}
                <div
                  style={{
                    position: "absolute",
                    left: 8,
                    top: 20,
                    width: 16,
                    height: 16,
                    borderRadius: "50%",
                    background: tc.text,
                    opacity: 0.9,
                    boxShadow: `0 0 12px ${tc.text}40`,
                  }}
                />

                {/* Card */}
                <div
                  style={{
                    background: "#111827",
                    border: "1px solid #1E293B",
                    borderRadius: 12,
                    overflow: "hidden",
                    transition: "border-color 0.2s",
                    borderColor: isOpen ? "#334155" : "#1E293B",
                  }}
                >
                  {/* Card header */}
                  <div
                    onClick={() => setExpanded(isOpen ? null : p.id)}
                    style={{
                      padding: "18px 20px",
                      cursor: "pointer",
                      display: "flex",
                      alignItems: "flex-start",
                      gap: 12,
                    }}
                  >
                    <div style={{ flex: 1 }}>
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 10,
                          marginBottom: 8,
                        }}
                      >
                        <span
                          style={{
                            fontSize: 11,
                            fontWeight: 600,
                            color: tc.text,
                            background: tc.bg,
                            border: `1px solid ${tc.border}`,
                            padding: "2px 10px",
                            borderRadius: 12,
                            fontFamily: "'JetBrains Mono', monospace",
                            letterSpacing: "0.02em",
                          }}
                        >
                          {p.tag}
                        </span>
                      </div>
                      <div
                        style={{
                          fontSize: 15,
                          fontWeight: 700,
                          color: "#E2E8F0",
                          lineHeight: 1.4,
                        }}
                      >
                        {p.title}
                      </div>
                    </div>
                    <div
                      style={{
                        fontSize: 18,
                        color: "#475569",
                        transform: isOpen ? "rotate(180deg)" : "rotate(0)",
                        transition: "transform 0.2s",
                        marginTop: 4,
                        flexShrink: 0,
                      }}
                    >
                      ▾
                    </div>
                  </div>

                  {/* Expanded content */}
                  {isOpen && (
                    <div
                      style={{
                        padding: "0 20px 20px",
                        borderTop: "1px solid #1E293B",
                      }}
                    >
                      {/* Symptom */}
                      <div style={{ marginTop: 16 }}>
                        <div
                          style={{
                            fontSize: 11,
                            fontWeight: 700,
                            color: "#EF4444",
                            marginBottom: 6,
                            fontFamily: "'JetBrains Mono', monospace",
                            letterSpacing: "0.08em",
                          }}
                        >
                          SYMPTOM
                        </div>
                        <div
                          style={{
                            fontSize: 13,
                            color: "#94A3B8",
                            lineHeight: 1.7,
                          }}
                        >
                          {p.symptom}
                        </div>
                      </div>

                      {/* Cause */}
                      <div style={{ marginTop: 16 }}>
                        <div
                          style={{
                            fontSize: 11,
                            fontWeight: 700,
                            color: "#F59E0B",
                            marginBottom: 6,
                            fontFamily: "'JetBrains Mono', monospace",
                            letterSpacing: "0.08em",
                          }}
                        >
                          ROOT CAUSE
                        </div>
                        <div
                          style={{
                            fontSize: 13,
                            color: "#94A3B8",
                            lineHeight: 1.7,
                          }}
                        >
                          {p.cause}
                        </div>
                      </div>

                      {/* Solution */}
                      <div style={{ marginTop: 16 }}>
                        <div
                          style={{
                            fontSize: 11,
                            fontWeight: 700,
                            color: "#10B981",
                            marginBottom: 6,
                            fontFamily: "'JetBrains Mono', monospace",
                            letterSpacing: "0.08em",
                          }}
                        >
                          SOLUTION
                        </div>
                        <div
                          style={{
                            fontSize: 13,
                            color: "#94A3B8",
                            lineHeight: 1.7,
                          }}
                        >
                          {p.solution}
                        </div>
                      </div>

                      {/* Code */}
                      <div style={{ marginTop: 16 }}>
                        <pre
                          style={{
                            background: "#0B0F1A",
                            border: "1px solid #1E293B",
                            borderRadius: 8,
                            padding: 16,
                            fontSize: 12,
                            lineHeight: 1.6,
                            color: "#CBD5E1",
                            fontFamily: "'JetBrains Mono', monospace",
                            overflowX: "auto",
                            margin: 0,
                            whiteSpace: "pre-wrap",
                            wordBreak: "break-word",
                          }}
                        >
                          {p.code}
                        </pre>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}