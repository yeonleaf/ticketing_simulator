package com.ticketing.domain.seat;

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
     * distanceScore = 1 - (row / maxRow)          → 앞줄일수록 1
     * centerScore   = 1 - |col - center| / center  → 중앙일수록 1
     * hotScore      = (distanceScore * distWeight + centerScore * centerWeight) * 100
     * UNIFORM은 모든 좌석 hotScore = 50
     */
    public int calculateHotScore(int row, int col, int maxRow, int maxCol) {
        if (this == UNIFORM) {
            return 50;
        }
        double center = maxCol / 2.0;
        double distanceScore = 1.0 - ((double) row / maxRow);
        double centerScore = 1.0 - Math.abs(col - center) / center;
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
        boolean isVipCol = Math.abs(col - center) <= center * vipColRatio;
        return (isVipRow && isVipCol) ? SeatGrade.VIP : SeatGrade.R;
    }
}
