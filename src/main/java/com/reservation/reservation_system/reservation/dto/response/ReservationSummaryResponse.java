package com.reservation.reservation_system.reservation.dto.response;

import com.reservation.reservation_system.reservation.entity.Reservation;
import com.reservation.reservation_system.reservation.entity.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationSummaryResponse(
        Long reservationId,
        String memberName,
        String productName,
        ReservationStatus status,
        LocalDateTime createdAt
) {

    public static ReservationSummaryResponse from(Reservation reservation) {
        return new ReservationSummaryResponse(
                reservation.getId(),
                reservation.getMember().getName(),
                reservation.getProduct().getName(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}
