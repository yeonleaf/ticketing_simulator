package com.ticketing.domain.audience;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AudienceRepository extends JpaRepository<Audience, Long> {
    List<Audience> findAllBySimulationId(Long simulationId);
}
