package com.reservation.reservation_system.common.exception.member;

import com.reservation.reservation_system.common.exception.BusinessException;

public class MemberException extends BusinessException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }
}
