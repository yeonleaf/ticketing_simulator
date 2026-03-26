package com.ticketing.domain.audience;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface AudienceRepository extends JpaRepository<Audience, Long> {

    @Query("SELECT a FROM Audience a " +
            "LEFT JOIN FETCH a.preferredSeatIds " +
            "LEFT JOIN FETCH a.acquiredSeatIds " +
            "WHERE a.simulationId = :simulationId")
    List<Audience> findAllBySimulationId(Long simulationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Audience a WHERE a.id = :id")
    Optional<Audience> findByIdForUpdate(Long id);

}
