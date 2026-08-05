package com.reservation.reservation_system.reservation.repository;

import com.reservation.reservation_system.reservation.entity.Reservation;
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
}
