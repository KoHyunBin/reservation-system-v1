package com.reservation.reservation_system.common.exception.reservation;

import com.reservation.reservation_system.common.exception.BusinessException;

public class ReservationException extends BusinessException {

    public ReservationException(ReservationErrorCode errorCode) {
        super(errorCode);
    }
}
