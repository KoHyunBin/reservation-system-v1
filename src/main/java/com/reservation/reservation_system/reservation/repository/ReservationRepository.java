package com.reservation.reservation_system.reservation.repository;

import com.reservation.reservation_system.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByMemberId(Long memberId);

    @Query("""
        select r
        from Reservation r
        join fetch r.member
        join fetch r.product
        where r.member.id = :memberId
        """)
    List<Reservation> findAllByMemberIdWithFetchJoin(
            @Param("memberId") Long memberId
    );

    //EntityGraph - 조회 쿼리, 로딩 전략을 분리한다
    //조회 조건은 메서드 이름을 만들어짐 - ByMemberId = where member_id = ?
    //로딩 전략은 @EntityGraph가 담당하고 핵심은 attributePaths -> member 와 product 함께 로딩
    @EntityGraph(attributePaths = {"member", "product"})
    List<Reservation> findAllWithEntityGraphByMemberId(Long memberId);
}
