package com.reservation.reservation_system.reservation.service.learning;

import com.reservation.reservation_system.common.exception.member.MemberErrorCode;
import com.reservation.reservation_system.common.exception.member.MemberException;
import com.reservation.reservation_system.common.exception.product.ProductErrorCode;
import com.reservation.reservation_system.common.exception.product.ProductException;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnsafeReservationService {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public ReservationResponse reserve(ReservationCreateRequest request) {
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(
                        () -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND)
                );

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(
                        () -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND)
                );


        product.decreaseStock();

        Reservation reservation = Reservation.create(member, product);

        reservationRepository.save(reservation);

        return ReservationResponse.from(reservation);
    }
}
