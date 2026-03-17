package com.ticketing.domain.simulation;

import com.ticketing.domain.audience.Audience;
import com.ticketing.domain.audience.AudienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationService {

    private final SimulationRepository simulationRepository;
    private final AudienceRepository audienceRepository;

    /**
     * Simulation 생성 (READY 상태) + 가상 Audience (audienceCount - 1)명 생성
     */
    @Transactional
    public Simulation createSimulation(Long showId, LockStrategy lockStrategy) {
        // TODO: Simulation 저장 + 가상 Audience 생성
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * 시뮬레이션 실행 (READY → RUNNING)
     * 가상 관객들이 jitter 간격으로 /seats/{seatNo}/hold API를 동시 호출
     * 완료 후 DONE으로 전환, 메트릭 집계
     */
    @Transactional
    public void startSimulation(Long simulationId) {
        // TODO: 가상 관객 동시 요청 로직 (Virtual Threads 활용)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Simulation getSimulation(Long simulationId) {
        return simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
    }

    public List<Audience> getSimulationAudiences(Long simulationId) {
        return audienceRepository.findAllBySimulationId(simulationId);
    }

    private List<Audience> createVirtualAudiences(Long simulationId, int count) {
        // TODO: audienceCount - 1명의 가상 관객 생성, strategy/seatCnt/jitter 랜덤 분배
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
