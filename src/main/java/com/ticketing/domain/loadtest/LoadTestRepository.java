package com.ticketing.domain.loadtest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadTestRepository extends JpaRepository<LoadTest, Long> {
}