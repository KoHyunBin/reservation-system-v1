package com.reservation.reservation_system.reservation.service.learning.optimistic;

import com.reservation.reservation_system.common.exception.optimistic.OptimisticLockRetryExhaustedException;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.dto.response.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 낙관적 락 충돌이 발생한 예약 요청의 재시도 정책을 담당한다.
 *
 * 트랜잭션을 직접 가지지 않고, 트랜잭션 서비스의 reserveOnce()를 반복해서
 * 호출한다. 따라서 한 번의 시도가 실패해 롤백된 뒤 다음 시도는 새로운
 * 트랜잭션에서 최신 상품 재고와 version을 다시 조회한다.
 */
@Component
@RequiredArgsConstructor
public class OptimisticLockReservationFacade {

    // 무한 재시도를 방지하기 위한 한 요청당 최대 충돌 허용 횟수다.
    private static final int MAX_RETRY_COUNT = 100;

    // 실제 예약 트랜잭션을 한 번 실행하는 별도의 Spring 빈이다.
    private final OptimisticLockReservationService reservationService;

    /**
     * 예약이 성공하거나, 재고 부족 등 재시도 대상이 아닌 예외가 발생하거나,
     * 낙관적 락 재시도 한도를 소진할 때까지 예약을 시도한다.
     */
    public ReservationResponse reserve(
            ReservationCreateRequest request
    ) {
        // 현재 요청에서 발생한 낙관적 락 충돌 횟수다. 요청마다 0부터 시작한다.
        int retryCount = 0;

        // 성공 시 return하고 한도 소진 시 예외를 던지므로 명시적인 종료 조건이 없다.
        while (true) {
            try {
                /*
                 * 한 번의 예약 트랜잭션을 실행한다.
                 * 성공하면 즉시 응답을 반환하며 반복문도 종료된다.
                 */
                return reservationService.reserveOnce(request);

            } catch (ObjectOptimisticLockingFailureException e) {
                /*
                 * 조회했던 version과 UPDATE 시점의 version이 달라 발생한
                 * 예상 가능한 동시 수정 충돌만 재시도한다.
                 *
                 * 재고 부족 ReservationException이나 DB deadlock에 해당하는
                 * CannotAcquireLockException은 여기서 잡지 않으므로 재시도와
                 * 별개로 호출자에게 전달된다.
                 */
                retryCount++;

                // 계속 충돌하면 시스템 자원을 무한히 사용하지 않도록 요청을 종료한다.
                if (retryCount >= MAX_RETRY_COUNT) {
                    throw new OptimisticLockRetryExhaustedException(
                            request.getProductId(),
                            retryCount,
                            e
                    );
                }

                // 다음 시도들이 다시 같은 순간에 몰리지 않도록 잠시 분산 대기한다.
                backoff(retryCount);
            }
        }
    }

    /**
     * 충돌한 요청들의 재시도 시점을 분산시키는 jitter backoff다.
     */
    private void backoff(int retryCount) {
        /*
         * 모든 실패 요청이 즉시 동시에 재시도하면
         * 다시 같은 시점에 충돌할 가능성이 크다.
         *
         * 재시도 횟수에 따라 최대 대기시간을 늘리고,
         * 실제 대기시간은 무작위로 선택한다.
         */
        // 재시도가 늘수록 최대 대기시간을 증가시키되 100ms를 넘지 않게 제한한다.
        long maximumDelay =
                Math.min(5L * retryCount, 100L);

        // 1ms부터 계산된 최대 대기시간 사이에서 무작위 지연값을 선택한다.
        long delay =
                ThreadLocalRandom.current()
                        .nextLong(1L, maximumDelay + 1L);

        try {
            // 현재 요청 스레드만 선택된 시간만큼 대기한 뒤 새로운 트랜잭션을 시도한다.
            Thread.sleep(delay);

        } catch (InterruptedException e) {
            // InterruptedException을 잡으면 해제되는 인터럽트 상태를 복원한다.
            Thread.currentThread().interrupt();

            // 인터럽트된 요청은 더 이상 재시도하지 않고 즉시 실패시킨다.
            throw new IllegalStateException(
                    "낙관적 락 재시도 중 인터럽트되었습니다.",
                    e
            );
        }
    }
}
