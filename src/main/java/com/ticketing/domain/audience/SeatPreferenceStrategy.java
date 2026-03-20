package com.ticketing.domain.audience;

import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatGrade;
import com.ticketing.domain.seat.SeatStatus;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum SeatPreferenceStrategy {

    HotScoreFirst {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            return Comparator.comparingInt(Seat::getHotScore).reversed()
                    .thenComparingInt(Seat::getNo);
        }
    },

    HotScoreLast {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            return Comparator.comparingInt(Seat::getHotScore)
                    .thenComparingInt(Seat::getNo);
        }
    },

    FrontCenter {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            double center = (maxCol - 1) / 2.0;
            return Comparator.comparingInt(Seat::getRow)
                    .thenComparingDouble(s -> Math.abs(s.getCol() - center))
                    .thenComparingInt(Seat::getNo);
        }
    },

    FrontRight {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            return Comparator.comparingInt(Seat::getRow)
                    .thenComparing(Seat::getCol, Comparator.reverseOrder())
                    .thenComparingInt(Seat::getNo);
        }
    },

    FrontLeft {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            return Comparator.comparingInt(Seat::getRow)
                    .thenComparingInt(Seat::getCol)
                    .thenComparingInt(Seat::getNo);
        }
    },

    CenterCenter {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            double center = (maxCol - 1) / 2.0;
            double midRow = (maxRow - 1) / 2.0;
            return Comparator.comparingDouble((Seat s) -> Math.abs(s.getRow() - midRow) + Math.abs(s.getCol() - center))
                    .thenComparingInt(Seat::getNo);
        }
    },

    BehindCenter {
        @Override
        public Predicate<Seat> filter() {
            return seat -> true;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            double center = (maxCol - 1) / 2.0;
            return Comparator.comparingInt(Seat::getRow).reversed()
                    .thenComparingDouble(s -> Math.abs(s.getCol() - center))
                    .thenComparingInt(Seat::getNo);
        }
    },

    GradeVIP {
        @Override
        public Predicate<Seat> filter() {
            return seat -> seat.getSeatGrade() == SeatGrade.VIP;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            return Comparator.comparingInt(Seat::getHotScore).reversed()
                    .thenComparingInt(Seat::getNo);
        }
    },

    GradeR {
        @Override
        public Predicate<Seat> filter() {
            return seat -> seat.getSeatGrade() == SeatGrade.R;
        }

        @Override
        public Comparator<Seat> comparator(int maxRow, int maxCol) {
            return Comparator.comparingInt(Seat::getHotScore).reversed()
                    .thenComparingInt(Seat::getNo);
        }
    };

    public abstract Predicate<Seat> filter();

    public abstract Comparator<Seat> comparator(int maxRow, int maxCol);

    public List<Integer> selectPreferred(List<Seat> seats, int seatCnt, int maxRow, int maxCol) {
        return selectPreferred(seats, seatCnt, maxRow, maxCol, new Random());
    }

    public List<Integer> selectPreferred(List<Seat> seats, int seatCnt, int maxRow, int maxCol, Random random) {
        // Step 1: filter — AVAILABLE + strategy filter (방어적 복사)
        List<Seat> filtered = new ArrayList<>(seats).stream()
                .filter(s -> s.getSeatStatus() == SeatStatus.AVAILABLE)
                .filter(filter())
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: sort
        filtered.sort(comparator(maxRow, maxCol));

        // Step 3: pool — 상위 seatCnt × 3개
        int poolSize = Math.min(seatCnt * 3, filtered.size());
        List<Seat> pool = new ArrayList<>(filtered.subList(0, poolSize));

        // Step 4: jitter — 풀 안에서 랜덤 셔플
        Collections.shuffle(pool, random);

        // Step 5: pick — 앞에서 seatCnt개
        int pickCnt = Math.min(seatCnt, pool.size());
        return pool.subList(0, pickCnt).stream()
                .map(Seat::getNo)
                .collect(Collectors.toList());
    }
}
