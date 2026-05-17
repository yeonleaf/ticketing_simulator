package com.ticketing.domain.audience;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query(value = "INSERT INTO audience_acquired_seats (audience_id, seat_id) VALUES (:audienceId, :seatId)",
            nativeQuery = true)
    void insertAcquiredSeat(@Param("audienceId") Long audienceId, @Param("seatId") Long seatId);

    @Modifying
    @Query(value = "DELETE s FROM audience_acquired_seats s where audience_id = :audienceId and seat_id = :seatId", nativeQuery = true)
    void deleteAcquiredSeat(@Param("audienceId") Long audienceId, @Param("seatId") Long seatId);
}
