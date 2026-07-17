package com.reservation.reservation_system.reservation.dto.response;

import com.reservation.reservation_system.reservation.entity.Reservation;
import com.reservation.reservation_system.reservation.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ReservationDetailResponse {

    private Long reservationId;
    private String memberName;
    private String productName;
    private ReservationStatus status;
    private LocalDateTime reservedAt;

    public static ReservationDetailResponse from(Reservation reservation) {
        return ReservationDetailResponse.builder()
                .reservationId(reservation.getId())
                .memberName(reservation.getMember().getName())
                .productName(reservation.getProduct().getName())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .build();
    }

}


