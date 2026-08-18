package com.reservation.reservation_system.reservation.service.learning.optimistic;

import com.reservation.reservation_system.common.exception.member.MemberErrorCode;
import com.reservation.reservation_system.common.exception.member.MemberException;
import com.reservation.reservation_system.common.exception.product.ProductErrorCode;
import com.reservation.reservation_system.common.exception.product.ProductException;
import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.member.repository.MemberRepository;
import com.reservation.reservation_system.product.entity.Product;
import com.reservation.reservation_system.product.repository.ProductRepository;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.dto.response.ReservationResponse;
import com.reservation.reservation_system.reservation.entity.Reservation;
import com.reservation.reservation_system.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 낙관적 락을 사용하는 예약의 '한 번의 시도'를 담당한다.
 *
 * 이 클래스는 충돌 재시도를 직접 반복하지 않는다. 하나의 트랜잭션 안에서
 * 회원과 상품을 조회하고, 재고를 감소시키고, 예약을 생성하는 역할만 한다.
 * 낙관적 락 충돌이 발생하면 현재 트랜잭션은 롤백되고 예외가 외부 Facade로
 * 전달된다.
 */
@Service
@RequiredArgsConstructor
public class OptimisticLockReservationService {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 예약을 한 번 시도한다.
     *
     * REQUIRES_NEW를 사용하므로 Facade가 이 메서드를 다시 호출할 때마다
     * 새로운 트랜잭션과 영속성 컨텍스트가 만들어진다. 충돌로 실패한
     * 트랜잭션을 재사용하지 않고 최신 stockQuantity와 version을 다시
     * 조회하기 위해 필요하다.
     *
     * Facade와 이 서비스를 서로 다른 Spring 빈으로 분리한 이유도
     * @Transactional 프록시를 거쳐 REQUIRES_NEW가 정상 적용되게 하기 위해서다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResponse reserveOnce(
            ReservationCreateRequest request
    ) {
        // 예약 주체인 회원을 조회한다. 회원 조회 자체는 낙관적 락 대상이 아니다.
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(
                        () -> new MemberException(
                                MemberErrorCode.MEMBER_NOT_FOUND
                        )
                );

        /*
         * 상품과 현재 version을 함께 조회한다.
         * findById()에 별도의 비관적 락을 선언하지 않았으므로 이 SELECT는
         * 상품 행을 선점해 다른 트랜잭션의 접근을 차단하지 않는다.
         */
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(
                        () -> new ProductException(
                                ProductErrorCode.PRODUCT_NOT_FOUND
                        )
                );

        /*
         * 최신 재시도에서 조회한 재고가 0이면 정상적인 비즈니스 실패다.
         * 낙관적 락 충돌과 재고 부족은 의미가 다르므로 서로 다른 예외로
         * 처리한다. 재고 부족은 다시 시도해도 성공할 수 없으므로 Facade에서
         * 재시도하지 않는다.
         */
        if (product.getStockQuantity() <= 0) {
            throw new ReservationException(
                    ReservationErrorCode.INSUFFICIENT_STOCK
            );
        }

        /*
         * 조회한 Product는 현재 영속성 컨텍스트가 관리하는 영속 상태다.
         * 여기서는 자바 객체의 재고 값만 감소하며 UPDATE가 즉시 실행된다고
         * 보장되지는 않는다. 트랜잭션 flush 시 더티 체킹 UPDATE가 실행된다.
         */
        product.decreaseStock();

        /*
         * 학습 테스트에서는 상품 UPDATE를 예약 INSERT보다 먼저 실행한다.
         *
         * 이렇게 하면 현재 관찰된
         * Reservation INSERT → FK 락 → Product UPDATE
         * 순서의 deadlock 영향을 줄이고,
         * Product의 version 충돌을 명확히 관찰할 수 있다.
         *
         * 이 flush에서 @Version 조건이 포함된 상품 UPDATE가 실행된다.
         * 다른 트랜잭션이 먼저 version을 변경했다면 UPDATE 결과가 0행이 되고
         * ObjectOptimisticLockingFailureException으로 변환될 예외가 발생한다.
         * 예외가 발생하면 아래 예약 생성과 INSERT는 실행되지 않고 현재
         * 트랜잭션 전체가 롤백된다.
         */
        productRepository.flush();

        // 상품 재고 UPDATE에 성공한 요청만 예약 엔티티를 생성한다.
        Reservation reservation =
                Reservation.create(member, product);

        // 새 예약을 영속성 컨텍스트에 등록한다. 트랜잭션 커밋까지 성공해야 확정된다.
        reservationRepository.save(reservation);

        // 커밋 과정에서 예외가 발생하면 호출자는 이 응답을 최종 결과로 받지 못한다.
        return ReservationResponse.from(reservation);
    }
}
