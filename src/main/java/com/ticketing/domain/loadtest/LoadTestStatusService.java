package com.ticketing.domain.loadtest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadTestStatusService {

    private final LoadTestRepository loadTestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoadTest updateRunning(Long loadTestId) {
        LoadTest lt = loadTestRepository.findById(loadTestId).orElseThrow();
        lt.start();
        return loadTestRepository.save(lt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDone(Long loadTestId) {
        LoadTest lt = loadTestRepository.findById(loadTestId).orElseThrow();
        lt.finish();
        loadTestRepository.save(lt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFail(Long loadTestId, String reason) {
        LoadTest lt = loadTestRepository.findById(loadTestId).orElseThrow();
        lt.fail(reason);
        loadTestRepository.save(lt);
    }
}