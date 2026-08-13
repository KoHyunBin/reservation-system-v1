package com.reservation.reservation_system.product.repository;

import com.reservation.reservation_system.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 조건부 update 사용
     * 재고가 1개 이상인 경우에만 재고 1개 감소
     * Select 후 객체에서 감소하는 방식 x
     * 재고 확인 과 감소를 하나의 update 쿼리에서 원자적으로 처리
     */
    @Modifying
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity - 1
            where p.id = :productId
                and p.stockQuantity > 0
            """)
    int decreaseStockAtomically(@Param("productId") Long productId);
}
