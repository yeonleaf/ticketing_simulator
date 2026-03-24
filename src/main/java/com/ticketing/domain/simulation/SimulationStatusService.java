package com.ticketing.domain.simulation;

import com.ticketing.domain.seat.Seat;
import com.ticketing.domain.seat.SeatRepository;
import com.ticketing.domain.seat.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SimulationStatusService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SimulationRepository simulationRepository;
    private final SeatRepository seatRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Simulation updateSimulationStatusStart(Long simulationId) {
        log.info("[Simulation {}] 시작합니다.", simulationId);
        Simulation simulation = simulationRepository.findById(simulationId).orElse(null);
        if (simulation == null) {
            throw new RuntimeException("잘못된 시뮬레이션 ID입니다.");
        }
        simulation.start();
        return simulationRepository.save(simulation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSimulationStatusFinish(Long simulationId, double totalTps, long avgResponseMs, int duplicateHoldCount, int fullySatisfiedCount, int partiallySatisfiedCount, int unsatisfiedCount) {
        log.info("[Simulation {}] 종료합니다.", simulationId);
        Simulation simulation = simulationRepository.findById(simulationId).orElse(null);
        if (simulation == null) {
            throw new RuntimeException("잘못된 시뮬레이션 ID입니다.");
        }
        simulation.finish(totalTps, avgResponseMs, duplicateHoldCount, fullySatisfiedCount, partiallySatisfiedCount, unsatisfiedCount);
        simulationRepository.save(simulation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSimulationStatusFail(Long simulationId, String message) {
        log.error("[Simulation {}] 실패했습니다.", message);
        Simulation simulation = simulationRepository.findById(simulationId).orElse(null);
        if (simulation == null) {
            throw new RuntimeException("잘못된 시뮬레이션 ID입니다.");
        }
        simulation.fail(message);
        simulationRepository.save(simulation);
    }

}
