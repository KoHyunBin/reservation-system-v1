package com.reservation.reservation_system.common.exception.product;

import com.reservation.reservation_system.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "P001",
            "없는 상품입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
