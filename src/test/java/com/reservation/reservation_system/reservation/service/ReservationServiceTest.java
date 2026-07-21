package com.reservation.reservation_system.reservation.service;

import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.member.repository.MemberRepository;
import com.reservation.reservation_system.product.entity.Product;
import com.reservation.reservation_system.product.repository.ProductRepository;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.dto.response.ReservationResponse;
import com.reservation.reservation_system.reservation.entity.Reservation;
import com.reservation.reservation_system.reservation.entity.ReservationStatus;
import com.reservation.reservation_system.reservation.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 *
 * 테스트 클래스에 @Transactional을 붙인 이유
 * 테스트가 끝나면 db를 깨끗하게 돌리기 위해서 - 테스트끼리 데이터가 섞이지 않는다
 * 테스트의 트랜잭션은 테스트 종료시 롤백한다
 * 비즈니스의 트랜잭션은 실제 비즈니스 트랜잭션이다
 */
@SpringBootTest
@Transactional
class ReservationServiceTest {


    @Autowired
    ReservationService reservationService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    EntityManager em;

    /**
     * 예약 생성 성공 테스트
     */
    @Test
    @DisplayName("예약을 생성하면 예약 상태는 RESERVED가 된다")
    void reserve_success() {
        //given
        Member member = memberRepository.save(Member.create("현빈", "khb4130@test.com"));
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 10));

        ReservationCreateRequest request = new ReservationCreateRequest(member.getId(), product.getId());

        //when
        ReservationResponse response = reservationService.reserve(request);

        //then
        assertThat(response.getReservationId()).isNotNull();
        assertThat(response.getMemberId()).isEqualTo(member.getId());
        assertThat(response.getProductId()).isEqualTo(product.getId());
        assertThat(response.getStatus().name()).isEqualTo("RESERVED");
    }

    /**
     * 재고 감소 테스트
     * 이 테스트가 핵심
     */
    @Test
    @DisplayName("예약을 생성하면 상품 재고가 1 감소한다")
    void reserve_decreaseStock() {
        //given
        Member member = memberRepository.save(Member.create("현빈", "khb4130@test.com"));
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 10));

        ReservationCreateRequest request = new ReservationCreateRequest(member.getId(), product.getId());

        /**
         * 트랜잭션 실행부분
         * 예약 성공
         * 재고 감소
         * productRepository.save(product)를 하지 않았다
         * 재고가 10 -> 9로 변경됐다.
         * 왜?
         * 영속성 컨테스트 + Dirty checking
         */
        //when
        reservationService.reserve(request);

        em.flush();
        em.clear();

        //then
        Product findProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(findProduct.getStockQuantity()).isEqualTo(9);

        //예약 엔티티에 예약 id 값이 있는지 확인
        assertThat(findProduct.getId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("재고가 없으면 예약 생성에 실패한다")
    /**
     * isInstanceOf() -> 예외 타입이 맞는지 확인
     * hasMessage() -> 메시지 맞는지 확인
     * reserve 메서드에서 -> product.decreaseStock 메서드 실행
     * decreaseStock  -> stockQuantity 0인 경우 예외 발생
     */
    void reserve_fail_when_stock_is_zero() {
        //given
        Member member = memberRepository.save(Member.create("현빈", "khb4130@test.com"));
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 0));

        //예약 생성
        ReservationCreateRequest request = new ReservationCreateRequest(member.getId(), product.getId());

        //when & then
        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("재고가 부족합니다");

        assertThat(reservationRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예약 생성에 실패한다")
    void reserve_fail_when_member_not_found() {
        //given
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 10));
        ReservationCreateRequest request = new ReservationCreateRequest(999L, product.getId());
        //when & then
        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 회원입니다.");
        assertThat(reservationRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 상품이면 예약 생성에 실패한다")
    void reserve_fail_when_product_not_found() {
        //given
        Member member = memberRepository.save(Member.create("현빈", "khb4130@test.com"));
        ReservationCreateRequest request = new ReservationCreateRequest(member.getId(), 999L);

        //when & then
        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 상품입니다.");

        assertThat(reservationRepository.count()).isEqualTo(0);

    }

    @Test
    @DisplayName("예약 취소 성공 테스트")
    void cancel_success() {
        //given
        Member member = memberRepository.save(Member.create("현빈", "khb4130@test.com"));
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 10));

        ReservationCreateRequest request = new ReservationCreateRequest(member.getId(), product.getId());

        ReservationResponse response = reservationService.reserve(request);

        //when
        reservationService.cancel(response.getReservationId());

        em.flush();
        em.clear();

        //then
        Reservation reservation = reservationRepository.findById(response.getReservationId())
                .orElseThrow();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);

        assertThat(reservation.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("예약을 취소하면 상품 재고가 1 복구된다")
    void cancel_restoreStock() {
        Member member = memberRepository.save(Member.create("현빈", "khb4130@test.com"));
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 10));

        ReservationCreateRequest request = new ReservationCreateRequest(member.getId(), product.getId());

        ReservationResponse response = reservationService.reserve(request);

        em.flush();
        em.clear();

        //when
        reservationService.cancel(response.getReservationId());

        em.flush();
        em.clear();

        //then
        Product findProduct = productRepository.findById(product.getId())
                .orElseThrow();

        assertThat(findProduct.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("이미 취소된 예약은 다시 취소할 수 없다")
    void cancel_fail_when_already_canceled() {
        //given
        Member member = memberRepository.save(Member.create("현빈", "cancel3@test.com"));
        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, 10));

        ReservationCreateRequest request =
                new ReservationCreateRequest(member.getId(), product.getId());

        ReservationResponse response = reservationService.reserve(request);

        reservationService.cancel(response.getReservationId());

        //when
        ReservationException exception = catchThrowableOfType(() ->
                reservationService.cancel(response.getReservationId()), ReservationException.class
        );

        //then
        assertThat(exception).isNotNull();
        assertThat(exception).hasMessage("이미 취소된 예약입니다.");
        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.ALREADY_CANCELLED);
    }

    @Test
    @DisplayName("존재하지 않는 예약은 취소할 수 없다")
    void cancel_fail_when_reservation_not_found() {

        //when & then
        assertThatThrownBy(() -> reservationService.cancel(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예약이 존재하지 않습니다.");
    }
}