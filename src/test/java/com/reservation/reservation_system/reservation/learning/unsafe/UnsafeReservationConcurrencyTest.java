package com.reservation.reservation_system.reservation.learning.unsafe;

import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.member.repository.MemberRepository;
import com.reservation.reservation_system.product.entity.Product;
import com.reservation.reservation_system.product.repository.ProductRepository;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.repository.ReservationRepository;
import com.reservation.reservation_system.reservation.service.learning.unsafe.UnsafeReservationService;
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
 * 락을 적용하지 않은 예약 서비스에서
 * 동시 예약 요청으로 재고 정합성이 깨지는지 확인하는 통합 테스트다.
 *
 * Mockito를 사용하는 단위 테스트가 아니라 실제 Spring 컨텍스트,
 * JPA 트랜잭션, 테스트 DB를 사용하는 통합 테스트다.
 */
@SpringBootTest

/**
 * application-test.yml 설정을 사용한다.
 */
@ActiveProfiles("test")
class UnsafeReservationConcurrencyTest {

    /**
     * 원자적 UPDATE나 락을 사용하지 않고
     * 상품을 조회한 뒤 엔티티의 재고를 감소시키는 학습용 서비스다.
     */
    @Autowired
    UnsafeReservationService unsafeReservationService;

    /**
     * 예약 데이터 삭제와 최종 예약 건수 조회에 사용한다.
     */
    @Autowired
    ReservationRepository reservationRepository;

    /**
     * 동시 요청에 사용할 회원 데이터를 생성하고 삭제할 때 사용한다.
     */
    @Autowired
    MemberRepository memberRepository;

    /**
     * 상품 생성, 삭제 및 최종 재고 조회에 사용한다.
     */
    @Autowired
    ProductRepository productRepository;


    /**
     * 각 테스트가 실행되기 전에 기존 데이터를 삭제한다.
     *
     * Reservation이 Member와 Product를 외래키로 참조하기 때문에
     * 자식 테이블인 Reservation부터 먼저 삭제해야 한다.
     */
    @BeforeEach
    void cleanDatabase() {

        // 예약은 회원과 상품을 참조하는 자식 데이터이므로 가장 먼저 삭제한다.
        reservationRepository.deleteAllInBatch();

        // 예약을 모두 삭제한 다음 상품을 삭제한다.
        productRepository.deleteAllInBatch();

        // 예약을 모두 삭제한 다음 회원을 삭제한다.
        memberRepository.deleteAllInBatch();
    }


    /**
     * 초기 재고가 10개인 상품에 100개의 요청을 동시에 실행한다.
     *
     * 락이 없는 경우 여러 트랜잭션이 같은 재고를 읽고 수정하면서
     * 갱신 유실이 발생하고 재고 정합성이 깨질 수 있다.
     */
    @Test
    @DisplayName("락이 없으면 동시 예약 요청에서 재고 정합성이 깨진다")
    void concurrentReservationWithoutLock() throws Exception {

        /*
         * =========================================================
         * 1. 테스트 조건 설정
         * =========================================================
         */

        // 상품의 초기 재고는 10개다.
        int stock = 10;

        // 동일한 상품에 예약 요청 100개를 동시에 실행한다.
        int requestCount = 100;


        /*
         * =========================================================
         * 2. 테스트 상품 생성
         * =========================================================
         */

        // 재고가 10개인 예약 상품을 DB에 저장한다.
        Product product = productRepository.save(
                Product.create(
                        "백엔드 컨퍼런스",
                        50_000,
                        stock
                )
        );

        /*
         * save()가 완료되면 IDENTITY 전략에 의해 상품 ID가 생성된다.
         * 이후 모든 예약 요청은 product.getId()를 사용해
         * 동일한 상품을 예약한다.
         */


        /*
         * =========================================================
         * 3. 요청에 사용할 회원 100명 생성
         * =========================================================
         */

        // 생성한 회원의 ID를 저장할 목록이다.
        List<Long> memberIds = new ArrayList<>();

        // 요청 수만큼 서로 다른 회원을 생성한다.
        for (int i = 0; i < requestCount; i++) {

            // 이메일 unique 제약조건에 걸리지 않도록 회원마다 다른 이메일을 사용한다.
            Member member = memberRepository.save(
                    Member.create(
                            "회원" + i,
                            "member" + i + "@test.com"
                    )
            );

            // 동시 예약 작업을 생성할 때 사용할 회원 ID를 목록에 저장한다.
            memberIds.add(member.getId());
        }


        /*
         * =========================================================
         * 4. 동시 요청을 실행할 스레드 풀 생성
         * =========================================================
         */

        /*
         * 요청 수와 동일한 100개의 작업 스레드를 생성한다.
         *
         * ready 래치를 100으로 설정하고 각 작업이 start.await()에서
         * 기다리는 구조이므로 모든 작업이 실행될 수 있도록
         * 스레드 수도 100개로 설정한다.
         *
         * 스레드 수를 32개로 설정하고 ready를 100으로 설정하면,
         * 먼저 실행된 32개 스레드가 start 신호를 기다리면서
         * 나머지 68개 작업이 실행되지 못하는 교착 상태가 생길 수 있다.
         */
        ExecutorService executorService =
                Executors.newFixedThreadPool(requestCount);


        /*
         * =========================================================
         * 5. 동시 실행을 제어할 CountDownLatch 생성
         * =========================================================
         */

        /*
         * 100개의 작업 스레드가 시작 지점에 모두 도착했는지 확인한다.
         *
         * 초기값: 100
         * 각 작업이 ready.countDown()을 실행할 때마다 1씩 감소한다.
         * 모든 작업이 준비되면 값이 0이 된다.
         */
        CountDownLatch ready =
                new CountDownLatch(requestCount);

        /*
         * 100개의 작업을 한 번에 출발시키기 위한 시작 신호다.
         *
         * 초기값은 1이다.
         * 작업 스레드는 start.await()에서 기다린다.
         * 메인 테스트 스레드가 start.countDown()을 실행하면
         * 값이 0이 되면서 대기 중인 모든 작업이 출발한다.
         */
        CountDownLatch start =
                new CountDownLatch(1);

        /*
         * 100개의 작업이 모두 끝났는지 확인하기 위한 종료 신호다.
         *
         * 초기값: 100
         * 각 작업은 성공 또는 실패와 관계없이
         * finally에서 done.countDown()을 실행한다.
         */
        CountDownLatch done =
                new CountDownLatch(requestCount);


        /*
         * =========================================================
         * 6. 동시 요청 결과를 저장할 변수 생성
         * =========================================================
         */

        /*
         * 여러 스레드가 동시에 성공 횟수를 증가시키므로
         * 일반 int가 아닌 스레드 안전한 AtomicInteger를 사용한다.
         */
        AtomicInteger successCount =
                new AtomicInteger();

        /*
         * 재고 부족으로 실패한 요청 수를 저장한다.
         */
        AtomicInteger insufficientStockCount =
                new AtomicInteger();

        /*
         * 여러 작업 스레드에서 발생한 예상하지 못한 예외를 저장한다.
         *
         * ArrayList는 여러 스레드의 동시 add()에 안전하지 않으므로
         * 스레드 안전한 ConcurrentLinkedQueue를 사용한다.
         */
        ConcurrentLinkedQueue<Throwable> unexpectedErrors =
                new ConcurrentLinkedQueue<>();


        /*
         * =========================================================
         * 7. 회원마다 예약 작업 하나씩 스레드 풀에 제출
         * =========================================================
         */

        try {

            // 100명의 회원 ID를 순회한다.
            for (Long memberId : memberIds) {

                /*
                 * 회원 한 명당 예약 작업 하나를 스레드 풀에 제출한다.
                 *
                 * submit()은 작업을 제출하고 바로 반환한다.
                 * 실제 작업은 스레드 풀의 작업 스레드에서 실행된다.
                 */
                executorService.submit(() -> {

                    /*
                     * 현재 작업이 시작 지점에 도착했음을 알린다.
                     *
                     * 100개의 작업이 이 코드를 실행하면
                     * ready 값이 100에서 0이 된다.
                     */
                    ready.countDown();

                    try {

                        /*
                         * 메인 테스트 스레드가 시작 신호를 보낼 때까지 기다린다.
                         *
                         * start 값이 1인 동안 대기한다.
                         * 메인 스레드가 start.countDown()을 호출하면
                         * 모든 작업이 동시에 다음 코드로 진행한다.
                         */
                        start.await();

                        /*
                         * 현재 회원 ID와 공통 상품 ID를 사용해
                         * 예약 생성 요청 객체를 만든다.
                         */
                        ReservationCreateRequest request =
                                new ReservationCreateRequest(
                                        memberId,
                                        product.getId()
                                );

                        /*
                         * 락이 적용되지 않은 예약 서비스를 호출한다.
                         *
                         * 서비스의 reserve()에는 @Transactional이 있으므로
                         * 작업 스레드마다 독립된 트랜잭션이 만들어진다.
                         *
                         * 여러 트랜잭션이 동시에 같은 상품 재고를 조회하면
                         * 같은 재고 값을 기준으로 decreaseStock()을 실행할 수 있다.
                         */
                        unsafeReservationService.reserve(request);

                        /*
                         * 서비스 호출과 트랜잭션 커밋이 예외 없이 끝났다면
                         * 예약 성공 횟수를 1 증가시킨다.
                         *
                         * incrementAndGet()은 여러 스레드가 동시에 호출해도
                         * 증가 연산이 유실되지 않는다.
                         */
                        successCount.incrementAndGet();

                    } catch (IllegalStateException e) {

                        /*
                         * 현재 Product.decreaseStock()은 재고가 0 이하이면
                         * IllegalStateException을 발생시킨다.
                         *
                         * 따라서 여기서는 해당 예외를 재고 부족 실패로 집계한다.
                         *
                         * 추후에는 IllegalStateException 전체를 재고 부족으로
                         * 판단하지 않고 전용 BusinessException과 에러 코드를
                         * 사용하는 것이 더 안전하다.
                         */
                        insufficientStockCount.incrementAndGet();

                    } catch (InterruptedException e) {

                        /*
                         * start.await()에서 대기하던 스레드가 인터럽트되면
                         * InterruptedException이 발생한다.
                         *
                         * 예외를 잡으면 인터럽트 상태가 해제되므로
                         * interrupt()를 다시 호출해서 상태를 복원한다.
                         */
                        Thread.currentThread().interrupt();

                        // 인터럽트는 정상적인 재고 부족이 아니므로 예상 밖 오류로 저장한다.
                        unexpectedErrors.add(e);

                    } catch (Throwable e) {

                        /*
                         * 재고 부족과 인터럽트 이외의 오류를 저장한다.
                         *
                         * 예:
                         * - DB 커넥션 획득 실패
                         * - SQL 오류
                         * - 회원 또는 상품 조회 실패
                         * - 트랜잭션 커밋 실패
                         *
                         * 작업 스레드에서 발생한 예외는 JUnit 테스트 스레드로
                         * 자동 전파되지 않기 때문에 직접 저장해야 한다.
                         */
                        unexpectedErrors.add(e);

                    } finally {

                        /*
                         * 성공과 실패 여부에 상관없이 현재 작업이 끝났음을 알린다.
                         *
                         * finally에 두어야 예상하지 못한 예외가 발생하더라도
                         * done 값이 반드시 감소한다.
                         */
                        done.countDown();
                    }
                });
            }


            /*
             * =====================================================
             * 8. 모든 작업이 출발 지점에 도착할 때까지 대기
             * =====================================================
             */

            /*
             * 최대 10초 동안 100개의 작업이 모두 준비되기를 기다린다.
             *
             * 모두 준비되면 true,
             * 10초 안에 준비되지 않으면 false를 반환한다.
             *
             * timeout을 사용해 테스트가 무한정 대기하는 것을 방지한다.
             */
            boolean allReady =
                    ready.await(10, TimeUnit.SECONDS);

            // 100개의 작업이 모두 준비되었는지 검증한다.
            assertThat(allReady).isTrue();


            /*
             * =====================================================
             * 9. 모든 작업에 공통 시작 신호 전송
             * =====================================================
             */

            /*
             * start 값을 1에서 0으로 만든다.
             *
             * start.await()에서 기다리던 100개의 작업이
             * 이 순간부터 예약 서비스를 호출하기 시작한다.
             */
            start.countDown();


            /*
             * =====================================================
             * 10. 모든 예약 작업이 끝날 때까지 대기
             * =====================================================
             */

            /*
             * 최대 30초 동안 100개의 작업이 모두 끝나기를 기다린다.
             *
             * 모든 작업이 done.countDown()을 호출하면 true,
             * 30초를 초과하면 false를 반환한다.
             */
            boolean completed =
                    done.await(30, TimeUnit.SECONDS);

            // 모든 요청이 제한 시간 안에 끝났는지 검증한다.
            assertThat(completed).isTrue();

        } finally {

            /*
             * ready 검증이나 done 검증이 실패해도
             * start.await()에서 대기하는 작업을 해제한다.
             *
             * 이미 start가 0이면 추가 countDown()은 아무 효과가 없다.
             */
            start.countDown();

            /*
             * 스레드 풀에 새로운 작업이 제출되지 않도록 종료를 요청한다.
             *
             * shutdown()은 이미 실행 중인 작업을 강제로 중단하지 않는다.
             */
            executorService.shutdown();
        }


        /*
         * =========================================================
         * 11. 모든 작업 완료 후 DB의 최종 상품 상태 조회
         * =========================================================
         */

        /*
         * 테스트 시작 시 생성했던 product 객체를 그대로 사용하지 않고
         * Repository를 통해 DB의 최신 상품 상태를 다시 조회한다.
         *
         * 기존 product 객체에는 다른 트랜잭션의 재고 변경이
         * 자동 반영되지 않을 수 있기 때문이다.
         */
        Product result = productRepository.findById(product.getId())
                .orElseThrow();


        /*
         * =========================================================
         * 12. DB에 실제 저장된 예약 건수 조회
         * =========================================================
         */

        /*
         * successCount는 테스트 코드가 집계한 성공 횟수다.
         *
         * 실제 DB에 저장된 예약 건수도 별도로 조회해서
         * 테스트 집계와 DB 결과가 같은지 확인한다.
         */
        long reservationCount =
                reservationRepository.countByProductId(product.getId());


        /*
         * =========================================================
         * 13. 테스트 결과 출력
         * =========================================================
         */

        System.out.println("초기 재고: " + stock);
        System.out.println("전체 요청: " + requestCount);
        System.out.println("예약 성공: " + successCount.get());
        System.out.println("재고 부족: " + insufficientStockCount.get());
        System.out.println("실제 예약 수: " + reservationCount);
        System.out.println("최종 재고: " + result.getStockQuantity());
        System.out.println("예상 밖 오류: " + unexpectedErrors.size());

        /*
         * 예상 밖 오류가 있다면 예외 내용을 출력한다.
         *
         * 단순히 오류 개수만 확인하면 정확한 실패 원인을 알기 어렵다.
         */
        unexpectedErrors.forEach(Throwable::printStackTrace);


        /*
         * =========================================================
         * 14. 예상하지 못한 오류가 없었는지 검증
         * =========================================================
         */

        /*
         * DB 오류나 트랜잭션 오류 때문에 요청이 실패한 것을
         * 재고 동시성 문제로 잘못 판단하지 않도록 검증한다.
         */
        assertThat(unexpectedErrors).isEmpty();


        /*
         * =========================================================
         * 15. 테스트가 집계한 성공 수와 DB 예약 수 비교
         * =========================================================
         */

        /*
         * 서비스가 정상적으로 반환되어 성공으로 집계된 횟수와
         * 실제 DB에 저장된 예약 건수가 같아야 한다.
         *
         * 이 검증은 테스트 코드가 결과를 올바르게 집계했는지
         * 확인하기 위한 보조 검증이다.
         */
        assertThat(reservationCount)
                .isEqualTo(successCount.get());


        /*
         * =========================================================
         * 16. 모든 요청이 성공 또는 재고 부족으로 처리됐는지 검증
         * =========================================================
         */

        /*
         * 예상하지 못한 오류가 없다면 100개의 요청은 모두
         * 예약 성공 또는 재고 부족 중 하나로 처리되어야 한다.
         */
        assertThat(
                successCount.get()
                        + insufficientStockCount.get()
        ).isEqualTo(requestCount);


        /*
         * =========================================================
         * 17. 재고 정합성 불변식 확인
         * =========================================================
         */

        /*
         * 정상적인 재고 시스템에서는 다음 식이 항상 성립해야 한다.
         *
         * 성공한 예약 수 + 최종 재고 = 초기 재고
         *
         * 예:
         * 예약 10건 + 최종 재고 0개 = 초기 재고 10개
         *
         * 락이 없는 현재 테스트에서는 갱신 유실로 인해
         * 이 식이 깨질 수 있다.
         */
        boolean consistencyBroken =
                reservationCount + result.getStockQuantity() != stock;
        /*
         * =========================================================
         * 18. 의도한 동시성 문제가 재현되었는지 검증
         * =========================================================
         */

        /*
         * 현재 테스트의 목적은 정상 동작이 아니라
         * 락이 없을 때 정합성이 깨지는 문제를 재현하는 것이다.
         *
         * 따라서 consistencyBroken이 true여야 테스트가 성공한다.
         *
         * 낙관적 락을 적용한 뒤에는 이 검증이 반대로 바뀌어야 한다.
         */
        assertThat(consistencyBroken).isTrue();
    }
}
