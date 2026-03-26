package com.ticketing.domain.show;

import com.ticketing.domain.audience.AudienceDistributionStrategy;
import com.ticketing.domain.simulation.LockStrategy;
import com.ticketing.domain.simulation.SimulationService;
import com.ticketing.domain.simulation.ThreadStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ShowService {

    private final ShowRepository showRepository;
    private final SimulationService simulationService;

    /**
     * Show 생성 + AudienceDistributionStrategy 2개 × LockStrategy 3개 × ThreadStrategy 2개 = 12개 Simulation 자동 생성
     */
    @Transactional
    public ShowResponse createShow(Show show) {
        show = showRepository.save(show);
        createSimulationsFor(show.getId());
        return new ShowResponse(show);
    }

    /**
     * LoadTest용 Show 생성 - sourceShow의 설정(maxRow, maxCol, seatSettingStrategy)을 그대로 사용하고 audienceCount만 변경
     */
    @Transactional
    public Show createShowForLoadTest(Long loadTestId, Show sourceShow, int audienceCount) {
        Show show = Show.builder()
                .name(sourceShow.getName() + " (부하테스트 audience=" + audienceCount + ")")
                .maxRow(sourceShow.getMaxRow())
                .maxCol(sourceShow.getMaxCol())
                .audienceCount(audienceCount)
                .seatSettingStrategy(sourceShow.getSeatSettingStrategy())
                .loadTestId(loadTestId)
                .build();
        show = showRepository.save(show);
        createSimulationsFor(show.getId());
        return show;
    }

    public Show getShow(Long showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
    }

    public List<Show> getAllShows() {
        return showRepository.findAll();
    }

    private void createSimulationsFor(Long showId) {
        for (AudienceDistributionStrategy audience : List.of(
                AudienceDistributionStrategy.MUSICAL_HEAVY_FRONT,
                AudienceDistributionStrategy.UNIFORM)) {
            for (LockStrategy lock : LockStrategy.values()) {
                for (ThreadStrategy thread : ThreadStrategy.values()) {
                    simulationService.createSimulation(showId, lock, thread, audience);
                }
            }
        }
    }
}
