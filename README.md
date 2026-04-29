
# 🎫 뮤지컬 티켓팅 시뮬레이터

300~1000 동시 사용자 환경에서 **Pessimistic Lock, Optimistic Lock, Redisson 분산 Lock**의 성능과 안정성을 정량 비교하는 프로젝트입니다.

> 📊 [인터랙티브 벤치마크 대시보드](https://yeonleaf.github.io/ticketing_simulator/)

<br>

## 기술 스택

Java 21 Virtual Threads · Spring Boot · Spring Data JPA · MySQL · Redis (Redisson) · k6 · GitHub Actions · AWS ECS Fargate · ALB · RDS · ECR

<br>

## 아키텍처
<img width="700" height="700" alt="ticketing_simulator_architecture" src="https://github.com/user-attachments/assets/7019e16b-9c33-4636-81aa-fa1f0d49a196" />

<br>

## 벤치마크 결과 요약

### Musical Standard (인기 좌석 편중 · VUS 300)

| | Pessimistic | Optimistic | Redisson |
|---|---|---|---|
| TPS (Platform / Virtual) | 101 / 72 | 158 / 116 | 145 / **127** |
| 평균 응답시간 (Platform / Virtual) | 1197 / 2100ms | 458 / 894ms | 342 / **615ms** |
| P95 (Platform / Virtual) | 5126 / 5918ms | 1478 / 2955ms | 1675 / **2403ms** |

- **Hotspot + Virtual Thread** 환경에서 Redisson이 TPS · 평균 · P95 모두 최선
- Pessimistic은 P95 5.9초로 tail latency 심각

### Uniform (균등 분산 · VUS 300)

| | Pessimistic | Optimistic | Redisson |
|---|---|---|---|
| TPS (Platform / Virtual) | 166 / 143 | **171** / **155** | 154 / 136 |
| 평균 응답시간 (Platform / Virtual) | 1489 / 797ms | 490 / 585ms | **172** / **444ms** |
| P95 (Platform / Virtual) | 1358 / 1815ms | **978** / 1511ms | 852 / 2037ms |

- 충돌이 분산되면 세 전략 간 TPS 차이가 10% 이내로 수렴
- Redisson Platform 응답시간(172ms)이 전 시나리오 통틀어 최저
  
<br>

### VUS 스케일링 (Musical Standard · Platform · 300→1000)

| VUS | Pessimistic | Optimistic | Redisson |
|---|---|---|---|
| 300 | 101 TPS / 1197ms | 158 TPS / 458ms | 145 TPS / 342ms |
| 500 | 88 TPS / 3706ms | 193 TPS / 987ms | 211 TPS / 934ms |
| 700 | 198 TPS / 767ms | 212 TPS / 628ms | **269 TPS** / 385ms |
| 1000 | 185 TPS / 336ms | **238 TPS** / 646ms | 220 TPS / **195ms** |

- Optimistic: TPS 선형 증가(158→238)
- Redisson: 700 VUS에서 TPS 피크(269) 후 하락하는 degradation curve 확인, 응답시간은 전 구간 안정
- Pessimistic: 500 VUS에서 P95 8.8초로 사실상 서비스 불가 수준
  
<br>

## 트러블슈팅

| 문제 | 원인 | 해결 |
|---|---|---|
| `UnexpectedRollbackException` | @Transactional 내부에서 예외를 catch해도 rollback-only 마킹 해제 안 됨 | TransactionTemplate으로 전환하여 트랜잭션 경계 명시적 제어 |
| Self-invocation으로 @Transactional 미적용 | 같은 클래스 내 호출은 Spring AOP 프록시 우회 | doHold()를 별도 InternalService로 분리 |
| Lock wait timeout (MySQL 1205) | 트랜잭션 내 Redis I/O로 DB row lock 보유 시간 증가 | 캐시 갱신 로직을 트랜잭션 밖으로 분리 |
| 캐시 분리 후 TPS 57% 악화 (160→69) | 분산 락 해제 ~ 캐시 갱신 사이 stale read 발생 | 캐시를 트랜잭션 밖 + 분산 락 안에 배치 |
| 실패 시에도 불필요한 캐시 I/O | 결과와 무관하게 매 요청 Redis 접근 | SUCCESS 조건 가드 추가 |
