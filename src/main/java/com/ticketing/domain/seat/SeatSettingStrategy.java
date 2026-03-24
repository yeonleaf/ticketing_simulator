package com.ticketing.domain.seat;

import java.util.ArrayList;
import java.util.List;

public enum SeatSettingStrategy {
    MUSICAL_STANDARD(0.55, 0.45, 0.45, 0.55),
    CONCERT_FRONT(0.8, 0.2, 0.5, 1.0),
    UNIFORM(0, 0, 0, 0);

    private final double distWeight;    // hotScore 계산 시 거리(row) 가중치
    private final double centerWeight;  // hotScore 계산 시 중앙도(col) 가중치
    private final double vipRowRatio;   // 앞쪽 몇%까지 VIP 후보 행
    private final double vipColRatio;   // 중앙 몇%까지 VIP 후보 열

    SeatSettingStrategy(double distWeight, double centerWeight, double vipRowRatio, double vipColRatio) {
        this.distWeight = distWeight;
        this.centerWeight = centerWeight;
        this.vipRowRatio = vipRowRatio;
        this.vipColRatio = vipColRatio;
    }

    /**
     * hotScore 계산
     * distanceScore = 1 - (row / (maxRow - 1))    → 앞줄(0)=1, 뒷줄(maxRow-1)=0
     * center        = maxCol / 2.0
     * centerScore   = 1 - |col - center| / center  → 중앙일수록 1
     * hotScore      = round((distanceScore * distWeight + centerScore * centerWeight) * 100)
     * UNIFORM은 모든 좌석 hotScore = 50
     *
     * 엣지케이스:
     * - maxRow == 1 → distanceScore = 1.0 (단일 행은 최전방)
     * - maxCol == 1 → centerScore = 1.0 (단일 열은 무조건 중앙)
     */
    public int calculateHotScore(int row, int col, int maxRow, int maxCol) {
        if (this == UNIFORM) {
            return 50;
        }
        double distanceScore = (maxRow <= 1) ? 1.0 : 1.0 - (double) row / (maxRow - 1);
        double center = maxCol / 2.0;
        double centerScore = (maxCol <= 1) ? 1.0 : 1.0 - Math.abs(col - center) / center;
        return (int) Math.round((distanceScore * distWeight + centerScore * centerWeight) * 100);
    }

    /**
     * VIP 여부 계산
     * VIP 조건: row < maxRow * vipRowRatio AND |col - center| <= center * vipColRatio
     * UNIFORM은 모든 좌석 R등급
     */
    public SeatGrade calculateGrade(int row, int col, int maxRow, int maxCol) {
        if (this == UNIFORM) {
            return SeatGrade.R;
        }
        double center = maxCol / 2.0;
        boolean isVipRow = row < maxRow * vipRowRatio;
        boolean isVipCol = (maxCol <= 1) || Math.abs(col - center) <= center * vipColRatio;
        return (isVipRow && isVipCol) ? SeatGrade.VIP : SeatGrade.R;
    }

    /**
     * showId, maxRow, maxCol 기반으로 전체 좌석 목록 생성.
     * 좌석 번호(no)는 1부터 maxRow*maxCol까지 행 우선(row-major) 순으로 부여.
     */
    public List<Seat> generateSeats(Long simulationId, int maxRow, int maxCol) {
        List<Seat> seats = new ArrayList<>(maxRow * maxCol);
        int seatNo = 1;
        for (int r = 0; r < maxRow; r++) {
            for (int c = 0; c < maxCol; c++) {
                seats.add(Seat.builder()
                        .no(seatNo++)
                        .simulationId(simulationId)
                        .row(r)
                        .col(c)
                        .seatStatus(SeatStatus.AVAILABLE)
                        .seatGrade(calculateGrade(r, c, maxRow, maxCol))
                        .hotScore(calculateHotScore(r, c, maxRow, maxCol))
                        .build());
            }
        }
        return seats;
    }
}
