package com.reservation.reservation_system.reservation.learning.optimistic;

import com.reservation.reservation_system.common.exception.optimistic.OptimisticLockRetryExhaustedException;
import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.member.repository.MemberRepository;
import com.reservation.reservation_system.product.entity.Product;
import com.reservation.reservation_system.product.repository.ProductRepository;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.repository.ReservationRepository;
import com.reservation.reservation_system.reservation.service.learning.optimistic.OptimisticLockReservationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일 상품에 여러 예약 요청이 동시에 들어올 때 낙관적 락과 재시도로
 * 재고 정합성이 지켜지는지 확인하는 통합 테스트다.
 *
 * 실제 Spring 트랜잭션 프록시, JPA, MySQL을 사용해야 @Version UPDATE와
 * 트랜잭션별 영속성 컨텍스트의 동작을 확인할 수 있으므로 @SpringBootTest를
 * 사용한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockReservationConcurrencyTest {

    /**
     * 낙관적 락 충돌 재시도를 담당하는 진입점이다.
     * 테스트가 트랜잭션 서비스를 직접 호출하면 충돌 요청이 재시도되지 않으므로
     * 반드시 Facade를 호출한다.
     */
    @Autowired
    OptimisticLockReservationFacade optimisticLockReservationFacade;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ProductRepository productRepository;

    /**
     * 테스트마다 동일한 초기 조건을 만들기 위해 기존 데이터를 삭제한다.
     * Reservation이 Product와 Member를 외래키로 참조하므로 자식 데이터인
     * Reservation을 가장 먼저 삭제한다.
     */
    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("재고 10개에 100개의 동시 요청이 들어오면 낙관적 락으로 정합성을 보장한다")
    void concurrentReservationWithOptimisticLock() throws Exception {
        /*
         * 재고보다 많은 요청을 동일 상품에 집중시켜 @Version 충돌을 발생시킨다.
         * 정합성이 지켜진다면 100개 요청 중 10개만 성공하고 90개는 재고 부족으로
         * 종료되어야 한다.
         */
        int stock = 10;
        int requestCount = 100;

        // version은 JPA가 관리하므로 상품 생성 코드에서 직접 지정하지 않는다.
        Product product = productRepository.save(
                Product.create("백엔드 컨퍼런스", 50_000, stock)
        );

        /*
         * 요청마다 서로 다른 회원을 사용한다. 회원 중복 같은 별도 정책이
         * 재고 동시성 테스트 결과에 영향을 주지 않게 하기 위함이다.
         */
        List<Long> memberIds = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            Member member = memberRepository.save(
                    Member.create(
                            "회원" + i,
                            "member" + i + "@test.com"
                    )
            );

            memberIds.add(member.getId());
        }

        /*
         * ready 래치가 요청 100개 모두의 준비를 기다리므로 스레드도 100개를
         * 생성한다. 스레드 수가 요청 수보다 작으면 먼저 실행된 작업들이
         * start.await()에서 대기하고 나머지 작업이 실행되지 못할 수 있다.
         */
        ExecutorService executorService =
                Executors.newFixedThreadPool(requestCount);

        /*
         * ready: 모든 작업이 공통 출발선에 도착했는지 확인한다.
         * start: 준비된 모든 작업을 최대한 동시에 출발시킨다.
         * done : 성공과 실패에 관계없이 모든 작업이 끝났는지 확인한다.
         */
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);

        // Facade가 정상 응답을 반환해 트랜잭션 커밋까지 성공한 요청 수다.
        AtomicInteger successCount = new AtomicInteger();

        // 최신 재고를 다시 조회한 결과 재고가 0이어서 종료된 정상 실패 수다.
        AtomicInteger insufficientStockCount = new AtomicInteger();

        // 정해진 횟수만큼 낙관적 락 충돌을 재시도하고도 실패한 요청 수다.
        AtomicInteger retryExhaustedCount = new AtomicInteger();

        /*
         * 낙관적 락 충돌과 재고 부족 이외의 예외를 보관한다.
         * 작업 스레드에서 발생한 예외는 JUnit 메인 스레드로 자동 전달되지 않으므로
         * 스레드 안전한 컬렉션에 저장한 뒤 테스트 마지막에 검증한다.
         */
        ConcurrentLinkedQueue<Throwable> unexpectedErrors =
                new ConcurrentLinkedQueue<>();

        try {
            for (Long memberId : memberIds) {
                executorService.submit(() -> {
                    // 현재 작업이 공통 출발선까지 도착했음을 메인 스레드에 알린다.
                    ready.countDown();

                    try {
                        // 메인 테스트 스레드가 start.countDown()을 호출할 때까지 기다린다.
                        start.await();

                        ReservationCreateRequest request =
                                new ReservationCreateRequest(
                                        memberId,
                                        product.getId()
                                );

                        /*
                         * Facade는 한 번의 예약 트랜잭션이 @Version 충돌로 실패하면
                         * backoff 후 새로운 트랜잭션으로 최신 재고와 version을
                         * 조회해 다시 시도한다.
                         */
                        optimisticLockReservationFacade.reserve(request);

                        // 예외 없이 반환됐다면 예약 트랜잭션이 커밋된 것이다.
                        successCount.incrementAndGet();

                    } catch (ReservationException e) {
                        /*
                         * 재고 부족은 예상한 비즈니스 실패다. 다른 종류의
                         * ReservationException은 숨기지 않고 예상 밖 오류로 보관한다.
                         */
                        if (e.getErrorCode()
                                == ReservationErrorCode.INSUFFICIENT_STOCK) {
                            insufficientStockCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(e);
                        }

                    } catch (OptimisticLockRetryExhaustedException e) {
                        /*
                         * 충돌 자체는 Facade 내부에서 처리된다. 이 예외는 최대
                         * 재시도 횟수까지 모두 충돌한 최종 실패만 나타낸다.
                         */
                        retryExhaustedCount.incrementAndGet();

                    } catch (InterruptedException e) {
                        // await()가 인터럽트되면 인터럽트 상태를 복원하고 오류를 보존한다.
                        Thread.currentThread().interrupt();
                        unexpectedErrors.add(e);

                    } catch (Throwable e) {
                        /*
                         * CannotAcquireLockException 같은 DB deadlock이나 SQL 오류는
                         * 낙관적 락 충돌과 다른 문제이므로 예상 밖 오류로 분리한다.
                         */
                        unexpectedErrors.add(e);

                    } finally {
                        // 어떤 결과로 끝나더라도 현재 작업의 완료를 반드시 알린다.
                        done.countDown();
                    }
                });
            }

            // 모든 작업이 10초 안에 출발선에 도착했는지 확인한다.
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            assertThat(allReady).isTrue();

            // 대기 중인 100개의 작업에 공통 시작 신호를 보낸다.
            start.countDown();

            // DB 경합과 재시도를 고려해 모든 요청의 완료를 최대 60초 기다린다.
            boolean completed = done.await(60, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

        } finally {
            /*
             * 준비 단계에서 assertion이 실패하더라도 start에서 기다리는 스레드를
             * 해제한 뒤 스레드 풀 종료를 요청한다. start가 이미 0이면 추가 호출은
             * 아무 효과가 없다.
             */
            start.countDown();
            executorService.shutdown();

            // 정상 종료되지 않은 작업이 있으면 테스트 종료 전에 중단을 요청한다.
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }

        /*
         * 테스트 시작 때 생성한 product 객체는 다른 트랜잭션의 변경을 자동으로
         * 반영하지 않으므로 Repository를 통해 DB의 최종 상태를 다시 조회한다.
         */
        Product result = productRepository.findById(product.getId())
                .orElseThrow();

        // 테스트 카운터와 별개로 DB에 실제 커밋된 예약 건수를 확인한다.
        long reservationCount =
                reservationRepository.countByProductId(product.getId());

        System.out.println("초기 재고: " + stock);
        System.out.println("전체 요청: " + requestCount);
        System.out.println("예약 성공: " + successCount.get());
        System.out.println("재고 부족: " + insufficientStockCount.get());
        System.out.println("재시도 소진: " + retryExhaustedCount.get());
        System.out.println("예상 밖 오류: " + unexpectedErrors.size());
        System.out.println("실제 예약 수: " + reservationCount);
        System.out.println("최종 재고: " + result.getStockQuantity());
        System.out.println("최종 버전: " + result.getVersion());

        // 실패 시 CannotAcquireLockException 등의 실제 원인까지 확인할 수 있게 출력한다.
        unexpectedErrors.forEach(Throwable::printStackTrace);

        // 낙관적 락 충돌은 Facade 내부에서 처리되어 예상 밖 오류로 남지 않아야 한다.
        assertThat(unexpectedErrors).isEmpty();

        // 학습 조건에서는 모든 성공 요청이 재시도 한도 안에서 처리되어야 한다.
        assertThat(retryExhaustedCount.get()).isZero();

        // 초기 재고가 10개이므로 예약 성공은 정확히 10건이어야 한다.
        assertThat(successCount.get()).isEqualTo(stock);

        // 나머지 90개 요청은 최신 재고가 0인 것을 확인하고 종료되어야 한다.
        assertThat(insufficientStockCount.get())
                .isEqualTo(requestCount - stock);

        // 애플리케이션 성공 카운터와 실제 DB 예약 건수가 일치해야 한다.
        assertThat(reservationCount).isEqualTo(successCount.get());
        assertThat(reservationCount).isEqualTo(stock);

        // 성공한 예약 10건이 각각 재고를 하나씩 감소시켜 최종 재고는 0이어야 한다.
        assertThat(result.getStockQuantity()).isZero();

        /*
         * 상품을 생성한 뒤 다른 UPDATE가 없다는 조건에서 성공한 상품 UPDATE마다
         * version이 1씩 증가하므로 최종 version은 성공 예약 수와 같아야 한다.
         */
        assertThat(result.getVersion()).isEqualTo((long) stock);

        // 재고 정합성의 핵심 불변식: 커밋된 예약 수 + 남은 재고 = 초기 재고.
        assertThat(
                reservationCount + result.getStockQuantity()
        ).isEqualTo(stock);

        // 100개 요청은 성공 또는 재고 부족 중 하나로 모두 분류되어야 한다.
        assertThat(
                successCount.get() + insufficientStockCount.get()
        ).isEqualTo(requestCount);
    }
}
