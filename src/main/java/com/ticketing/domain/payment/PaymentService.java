package com.ticketing.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제 처리 (PENDING → COMPLETED)
     */
    @Transactional
    public Payment processPayment(Long audienceId, List<Integer> seatNoList) {
        // TODO: 결제 처리 로직
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }
}
