package com.ticketing.domain.loadtest;

import com.ticketing.domain.show.Show;
import com.ticketing.domain.show.ShowRepository;
import com.ticketing.domain.show.ShowService;
import com.ticketing.domain.simulation.Simulation;
import com.ticketing.domain.simulation.SimulationRepository;
import com.ticketing.domain.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LoadTestService {

    private final LoadTestRepository loadTestRepository;
    private final LoadTestStatusService loadTestStatusService;
    private final ShowService showService;
    private final ShowRepository showRepository;
    private final SimulationRepository simulationRepository;
    private final SimulationService simulationService;

    @Transactional
    public LoadTest createLoadTest(Long sourceShowId, int startAudience, int endAudience, int step) {
        // sourceShow 존재 확인
        showRepository.findById(sourceShowId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + sourceShowId));
        return loadTestRepository.save(LoadTest.builder()
                .sourceShowId(sourceShowId)
                .startAudience(startAudience)
                .endAudience(endAudience)
                .step(step)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runLoadTest(Long loadTestId) {
        loadTestStatusService.updateRunning(loadTestId);
        try {
            LoadTest lt = loadTestRepository.findById(loadTestId).orElseThrow();
            Show sourceShow = showRepository.findById(lt.getSourceShowId()).orElseThrow();

            for (int audienceCount = lt.getStartAudience();
                 audienceCount <= lt.getEndAudience();
                 audienceCount += lt.getStep()) {

                Show show = showService.createShowForLoadTest(loadTestId, sourceShow, audienceCount);
                List<Simulation> simulations = simulationRepository.findByShowId(show.getId());
                for (Simulation sim : simulations) {
                    simulationService.runSimulationSync(sim.getId());
                }
            }
            loadTestStatusService.updateDone(loadTestId);
        } catch (Exception e) {
            loadTestStatusService.updateFail(loadTestId, e.getMessage());
            throw e;
        }
    }

    public LoadTestReportDto getReport(Long loadTestId) {
        LoadTest lt = loadTestRepository.findById(loadTestId)
                .orElseThrow(() -> new IllegalArgumentException("LoadTest not found: " + loadTestId));
        Show sourceShow = showRepository.findById(lt.getSourceShowId()).orElseThrow();

        List<LoadTestReportDto.AudienceResult> results = showRepository.findByLoadTestId(loadTestId)
                .stream()
                .sorted(Comparator.comparingInt(Show::getAudienceCount))
                .map(show -> {
                    List<LoadTestReportDto.SimulationResult> simResults = simulationRepository.findByShowId(show.getId())
                            .stream()
                            .map(s -> new LoadTestReportDto.SimulationResult(
                                    s.getId(),
                                    s.getLockStrategy(),
                                    s.getThreadStrategy(),
                                    s.getAudienceDistributionStrategy(),
                                    s.getStatus(),
                                    s.getTotalTps(),
                                    s.getAvgResponseMs(),
                                    s.getDuplicateHoldCount(),
                                    s.getFullySatisfiedCount(),
                                    s.getPartiallySatisfiedCount(),
                                    s.getUnsatisfiedCount()
                            ))
                            .toList();
                    return new LoadTestReportDto.AudienceResult(show.getAudienceCount(), simResults);
                })
                .toList();

        return new LoadTestReportDto(
                lt.getId(),
                sourceShow.getId(),
                sourceShow.getName(),
                lt.getStatus(),
                lt.getStartAudience(),
                lt.getEndAudience(),
                lt.getStep(),
                results);
    }
}
