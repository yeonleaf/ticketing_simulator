package com.ticketing.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payments
     * 결제 처리 (PENDING → COMPLETED)
     */
    @PostMapping
    public ResponseEntity<Payment> processPayment(@RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.processPayment(request.audienceId(), request.seatNoList()));
    }

    public record PaymentRequest(Long audienceId, List<Integer> seatNoList) {}
}
