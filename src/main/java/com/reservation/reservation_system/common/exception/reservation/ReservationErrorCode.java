package com.reservation.reservation_system.common.exception.reservation;

import com.reservation.reservation_system.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {


    RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "R001",
            "예약을 찾을 수 없습니다."
    ),

    INSUFFICIENT_STOCK(
            HttpStatus.CONFLICT,
            "R002",
            "예약 가능한 재고가 부족합니다."
    ),

    ALREADY_CANCELLED(
            HttpStatus.CONFLICT,
            "R003",
            "이미 취소된 예약입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

}
