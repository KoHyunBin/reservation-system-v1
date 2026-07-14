package com.reservation.reservation_system.reservation.entity;

import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.global.BaseTimeEntity;
import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime reservedAt;
    private LocalDateTime canceledAt;

    public Reservation(Member member, Product product) {
        this.member = member;
        this.product = product;
        this.status = ReservationStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
    }

    public static Reservation create(Member member, Product product) {
        return new Reservation(member, product);
    }


    // 예약 취소
    /**
     * 예약 취소 시 예약 상태 바꾼다.
     * 예약 취소 시 상품 재고 복구한다.
     * 취소가 된 상태에서 또 취소 실행하면
     * 예약 상태 값을 비교 enum = CANCELED
     * status가 CANCELED이고 참이면 예약 예외를 발생한다
     */
    public void cancel() {
        if (this.status == ReservationStatus.CANCELED) {
            throw new ReservationException(
                    ReservationErrorCode.ALREADY_CANCELLED
            );
        }
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.product.increaseStock();
    }
}
