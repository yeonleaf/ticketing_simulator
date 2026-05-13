package com.ticketing.domain.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllBySimulationId(Long simulationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT s FROM Seat s WHERE s.simulationId = :simulationId and s.seatStatus = SeatStatus.AVAILABLE")
    List<Seat> findEmptySeatsBySimulationId(Long simulationId);


    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE seats " +
            "SET seat_status = :status, holder_id = :holderId, version = version + 1 " +
            "WHERE id = :id AND version = :version",
            nativeQuery = true)
    int updateIfVersionMatches(@Param("id") Long id,
                               @Param("version") Long version,
                               @Param("status") String status,
                               @Param("holderId") Long holderId);

}
