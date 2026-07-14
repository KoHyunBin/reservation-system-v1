package com.reservation.reservation_system.reservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReservationCreateRequest {

    @NotNull(message = "회원 ID는 필수입니다.")
    private Long memberId;
    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    public ReservationCreateRequest(Long memberId, Long productId) {
        this.memberId = memberId;
        this.productId = productId;
    }
}
