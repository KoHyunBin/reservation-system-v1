package com.reservation.reservation_system.common.exception.member;

import com.reservation.reservation_system.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "M001",
            "존재하지 않는 회원입니다"
    );


    private final HttpStatus status;
    private final String code;
    private final String message;
}
