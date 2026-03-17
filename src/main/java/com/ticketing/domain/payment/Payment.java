package com.ticketing.domain.payment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long audienceId;

    @ElementCollection
    @CollectionTable(name = "payment_seat_nos", joinColumns = @JoinColumn(name = "payment_id"))
    @Column(name = "seat_no")
    private List<Integer> seatNoList = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Builder
    public Payment(Long audienceId, List<Integer> seatNoList) {
        this.audienceId = audienceId;
        this.seatNoList = new ArrayList<>(seatNoList);
        this.paymentStatus = PaymentStatus.PENDING;
    }

    public void complete() {
        this.paymentStatus = PaymentStatus.COMPLETED;
    }
}
