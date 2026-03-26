package com.ticketing.domain.show;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByLoadTestId(Long loadTestId);
}
