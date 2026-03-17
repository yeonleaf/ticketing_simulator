package com.ticketing.domain.audience;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AudienceService {

    private final AudienceRepository audienceRepository;

    public Audience getAudience(Long audienceId) {
        return audienceRepository.findById(audienceId)
                .orElseThrow(() -> new IllegalArgumentException("Audience not found: " + audienceId));
    }

    public List<Audience> getAudiencesBySimulation(Long simulationId) {
        return audienceRepository.findAllBySimulationId(simulationId);
    }

    @Transactional
    public Audience save(Audience audience) {
        return audienceRepository.save(audience);
    }
}
