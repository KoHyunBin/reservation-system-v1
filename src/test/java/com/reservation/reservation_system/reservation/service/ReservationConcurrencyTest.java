package com.reservation.reservation_system.reservation.service;

import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.member.repository.MemberRepository;
import com.reservation.reservation_system.product.entity.Product;
import com.reservation.reservation_system.product.repository.ProductRepository;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.repository.ReservationRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ReservationConcurrencyTest {

    @Autowired
    ReservationService reservationService;
    @Autowired
    ReservationRepository reservationRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    ProductRepository productRepository;

    @BeforeEach
    void cleanDatabase() {
        // FK를 참조하는 자식 데이터부터 삭제한다.
        reservationRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("재고 10개 상품에 100개의 동시 예약 요청 시 재고 정합성을 검증한다")
    void concurrentReservation() throws Exception {
        // 테스트 조건 설정
        // 상품 재고 10개 생성, 예약 요청 100개 생성
        int stock = 10;
        int requestCount = 100;


        Product product = productRepository.save(Product.create("백엔드 컨퍼런스", 50000, stock));

        // 100명의 회원 ID를 저장할 목록을 생성한다.
        List<Long> memberIds = new ArrayList<>();

        // 반복문을 통해 100명의 서로 다른 회원을 만든다
        for (int i = 0; i < requestCount; i++) {
            Member member = memberRepository.save(
                    Member.create("회원"+i,"member"+i+"@test.com")
            );
            memberIds.add(member.getId());
        }

        // 스레드 풀 생성
        // 100개의 작업을 처리할 스레드 100개를 생성한다.
        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        // 100개의 작업 스레드가 시작 지점에 도달했는지 확인하기 위해 생성
        CountDownLatch ready = new CountDownLatch(requestCount);

        // 작업 준비가 된 100개의 스레드를 한 번에 출발시키는 출발 신호
        // start.await()에서 작업 스레드 기다리고있다
        // start.countDown 실행하면 start 값이 1 -> 0이 되면서 대기중인 100개 스레드가 예약 요청 시작
        CountDownLatch start = new CountDownLatch(1);

        // 최종 결과 검증은 100개 작업이 모두 끝난 후 실행해야 한다.
        // 작업 도중 조회하면 일부 트랜잭션만 반영된 중간 상태를 최종 결과로 오해할 수 있다.
        // 각 작업은 성공·실패와 관계없이 finally에서 done 값을 감소시킨다.
        CountDownLatch done = new CountDownLatch(requestCount);


        // 원자적 카운터
        // 여러 스레드가 동시에 성공·실패 횟수를 변경하므로 int 대신 AtomicInteger 사용
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger insufficientStockCount = new AtomicInteger();
        AtomicInteger interruptedCount = new AtomicInteger();

        // 여러 스레드에서 발생한 예상하지 못한 예외를 안전하게 보관한다.
        // 기존처럼 모든 예외를 failureCount에 포함하면 DB 오류나 회원 조회 오류까지
        // 정상적인 "재고 부족 실패"로 오해할 수 있기 때문에 예외 원인을 따로 보존한다.
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        try {
            // 회원id마다 예약 작업 하나를 스레드 풀에 제출한다
            // 총 100명 회원이 있으니깐 작업도 100개
            for (Long memberId : memberIds) {
                executor.submit(() -> {

                    // 현재 작업 스레드가 시작 지점에 도달했음을 메인 테스트 스레드에 알린다.
                    ready.countDown();

                    try {
                        // 메인 테스트 스레드가 공통 시작 신호를 보낼 때까지 대기한다.
                        start.await();

                        // 메인 테스트 스레드가 start.countDown()을 호출하면 예약 로직을 실행한다.
                        // reserve()의 트랜잭션은 작업 스레드마다 독립적으로 실행된다.
                        // 여러 트랜잭션이 변경 전의 같은 재고를 읽으면 갱신 유실로 정합성 문제가 발생할 수 있다.
                        reservationService.reserve(
                                new ReservationCreateRequest(
                                        memberId,
                                        product.getId()
                                )
                        );

                        // 예외가 없으면 예약 성공 횟수 증가
                        successCount.incrementAndGet();
                    } catch (ReservationException e) {
                        // 서비스에서 전달한 ReservationException의 에러 코드를 확인한다.
                        // INSUFFICIENT_STOCK은 동시 예약 과정에서 예상한 정상적인 재고 부족 실패다.
                        if (e.getErrorCode() == ReservationErrorCode.INSUFFICIENT_STOCK) {
                            failureCount.incrementAndGet();
                            insufficientStockCount.incrementAndGet();
                        } else {
                            // 다른 ReservationException은 이번 테스트에서 예상한 실패가 아니므로
                            // 원인을 잃지 않도록 큐에 예외 객체 자체를 저장한다.
                            unexpectedErrors.add(e);
                        }
                    } catch (InterruptedException e) {
                        // 대기 중인 스레드가 인터럽트되면 인터럽트 상태를 복원하고 원인을 보존한다.
                        Thread.currentThread().interrupt();
                        interruptedCount.incrementAndGet();
                        unexpectedErrors.add(e);
                    } catch (Exception e) {
                        // 재고 부족 이외의 예외는 숨기지 않고 테스트 마지막에 검증한다.
                        unexpectedErrors.add(e);
                    } finally {
                        // 성공, 실패 횟수 상관없이 작업 완료 처리
                        done.countDown();
                    }
                });
            }

            // 100개의 작업 스레드가 시작 지점에 도달할 때까지 기다린다.
            ready.await();
            // 대기 중인 100개 작업 스레드에 공통 시작 신호를 보낸다.
            start.countDown();
            // 최종 DB 상태를 조회하기 전에 100개 작업이 모두 끝날 때까지 기다린다.
            done.await();

        } finally {
            System.out.println("예약 성공: " + successCount.get());
            System.out.println("재고 부족: " + insufficientStockCount.get());
            System.out.println("인터럽트: " + interruptedCount.get());
            System.out.println("그 외 예외: " + unexpectedErrors.size());
            // 새로운 작업을 받지 않도록 스레드 풀 종료를 요청한다.
            // shutdown()은 이미 제출된 작업을 중단하지 않으며, finally를 사용해 예외 발생 시에도 호출한다.
            executor.shutdown();
        }

        Product result = productRepository.findById(product.getId())
                .orElseThrow();

        long reservationCount =
                reservationRepository.countByProductId(product.getId());


        // 100개의 요청이 모두 성공 또는 재고 부족 실패 중 하나로 처리됐는지 확인한다.
        assertThat(successCount.get() + failureCount.get()).isEqualTo(requestCount);
        // 정합성이 지켜지면 재고 10개를 제외한 나머지 90개 요청은 실패해야 한다.
        assertThat(failureCount.get()).isEqualTo(requestCount - stock);
        // 예약 성공 횟수가 초기 재고 10개를 초과하지 않았는지 검증한다.
        assertThat(successCount.get()).isEqualTo(stock);
        // 애플리케이션의 성공 횟수뿐 아니라 실제 DB 예약 건수도 10건인지 검증한다.
        assertThat(reservationCount).isEqualTo(stock);
        // 10개의 정상 예약이 처리된 후 최종 재고가 0인지 검증한다.
        assertThat(result.getStockQuantity()).isZero();

    }
}
