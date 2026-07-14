package com.reservation.reservation_system.reservation.dto;

import com.reservation.reservation_system.reservation.entity.Reservation;
import com.reservation.reservation_system.reservation.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private Long reservationId;
    private Long memberId;
    private Long productId;
    private ReservationStatus status;
    private LocalDateTime reservedAt;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .memberId(reservation.getMember().getId()) // flush 발생
                .productId(reservation.getProduct().getId()) //flush 발생
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .build();
    }

}
