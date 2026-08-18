package com.reservation.reservation_system.common.exception.optimistic;

/**
 * 한 요청이 허용된 낙관적 락 재시도 횟수를 모두 사용했음을 나타낸다.
 *
 * ObjectOptimisticLockingFailureException은 개별 시도의 충돌을 의미하지만,
 * 이 예외는 여러 차례 새로운 트랜잭션으로 다시 시도했음에도 끝내 예약을
 * 완료하지 못했다는 애플리케이션 수준의 최종 실패를 의미한다.
 */
public class OptimisticLockRetryExhaustedException
        extends RuntimeException {

    /**
     * 장애 로그에서 충돌 대상 상품과 실제 재시도 횟수를 확인할 수 있도록
     * 메시지에 두 값을 포함하고, 마지막 낙관적 락 예외를 원인으로 보존한다.
     */
    public OptimisticLockRetryExhaustedException(
            Long productId,
            int retryCount,
            Throwable cause
    ) {
        super(
                "낙관적 락 재시도를 소진했습니다. productId="
                        + productId
                        + ", retryCount="
                        + retryCount,
                cause
        );
    }
}
