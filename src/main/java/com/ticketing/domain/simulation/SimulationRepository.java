package com.ticketing.domain.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationRepository extends JpaRepository<Simulation, Long> {

    List<Simulation> findByShowId(Long showId);

    List<Simulation> findByShowIdAndStatus(Long showId, SimStatus status);
}
