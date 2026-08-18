package com.reservation.reservation_system.common.exception.product;

import com.reservation.reservation_system.common.exception.BusinessException;

public class ProductException extends BusinessException {
    public ProductException(ProductErrorCode errorCode) {
        super(errorCode);
    }
}
