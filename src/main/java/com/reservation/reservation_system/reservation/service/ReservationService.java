package com.reservation.reservation_system.reservation.service;

import com.reservation.reservation_system.common.exception.member.MemberErrorCode;
import com.reservation.reservation_system.common.exception.member.MemberException;
import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.member.entity.Member;
import com.reservation.reservation_system.member.repository.MemberRepository;
import com.reservation.reservation_system.product.entity.Product;
import com.reservation.reservation_system.product.repository.ProductRepository;
import com.reservation.reservation_system.reservation.dto.response.ReservationDetailResponse;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.dto.response.ReservationResponse;
import com.reservation.reservation_system.reservation.dto.response.ReservationSummaryResponse;
import com.reservation.reservation_system.reservation.entity.Reservation;
import com.reservation.reservation_system.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;


    /**
     * controller - 예약 api 요청 -> reservationService.reserve() 메서드 실행
     * 어떤 회원이 어떤 상품을 예약했는지 알아야하기 때문에 회원id,상품id 조회 필요
     * db에서 해당 id가 없으면 에러 발생
     *
     */
    @Transactional
    public ReservationResponse reserve(ReservationCreateRequest request) {
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        /**
         * product 조회 시 -> 영속성 컨텍스트에 저장
         * jpa는 product의 최초 상태를 스냅샷으로 저장
         * stockQuantity가 100이면 100으로 저장
         */
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));

        /**
         * 해당상품 현재 재고에서 감소
         * productRepository.save(product)를 하지 않는다
         * 그런데도 DB에 재고 감소가 반영된다
         * 왜? product가 트랜잭션 안에서 조회된 영속 상태 entity이기 때문이다
         * 메서드를 실행해도 DB UPDATE가 바로 나가지 않는다
         * 그냥 자바 객체 값만 바뀐다.
         * stockQuantity = 99
         */
        product.decreaseStock();

        // 회원이 상품을 예약 성공하면 예약엔티티 값을 넣는다
        Reservation reservation = Reservation.create(member, product);

        /**
         * 새로운 reservation은 아직 db에 없는 객체다
         * 그래서 save()를 하면 영속성 컨텍스트에 등록된다
         */
        reservationRepository.save(reservation);

        return ReservationResponse.from(reservation);
    }

    /**
     * 예약 취소 메서드
     * 예약 취소 시 -> 예약 상태 변경, 상품 재고 증가
     * 왜? -> reservation.cancel()만 했는데 update sql이 2개 나가는가?
     * 예약 entity -> status 변경 -> dirty checking -> update sql
     * 상품 entity -> increaseStock -> dirty checking -> update sql
     * flush 시점 -> update 2개 -> commit
     */
    @Transactional
    public void cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약이 존재하지 않습니다."));

        reservation.cancel();
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponse findReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));
        return ReservationDetailResponse.from(reservation);
    }

    //예약 목록 조회
    @Transactional(readOnly = true)
    public List<ReservationSummaryResponse> findReservationsByMemberId(Long memberId) {
        //회원테이블에 회원이 있는지 확인한다 회원이 없으면 예외 발생
        memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        //해당 회원에 예약 목록 건수 조회
        List<Reservation> reservations = reservationRepository.findAllByMemberId(memberId);

        //예약 목록 건수를 담을 List 응답 객체 생성
        List<ReservationSummaryResponse> responses = new ArrayList<>();

        //List 예약 목록 객체를 List 응답 객체에 담아야한다.
        //예약 목록 객체들을 한번 씩 순회하면서 예약 객체로 생성하고
        //예약 객체를 List 응답 객체에 추가한고 responses를 반환한다.
        for (Reservation reservation : reservations) {
            ReservationSummaryResponse response = ReservationSummaryResponse.from(reservation);
            responses.add(response);
        }
        return responses;
    }
}
