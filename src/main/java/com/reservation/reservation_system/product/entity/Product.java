package com.reservation.reservation_system.product.entity;

import com.reservation.reservation_system.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id; //재고 ID

    @Column(nullable = false)
    private String name; //재고 이름

    private int price; //재고 가격

    private int stockQuantity; // 재고 수량

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    /**
     * JPA 낙관적 락 충돌을 감지하기 위한 버전 값이다.
     *
     * 트랜잭션이 Product를 조회하면 조회 당시의 version도 함께 기억한다.
     * 이후 더티 체킹으로 Product를 수정할 때 JPA는 개념적으로 다음과 같이
     * 식별자와 조회 당시 버전을 모두 조건으로 사용하는 UPDATE를 실행한다.
     *
     * update product
     *    set stock_quantity = ?, version = version + 1
     *  where product_id = ? and version = ?
     *
     * 먼저 커밋한 트랜잭션이 version을 증가시키면, 이전 version으로 수정하려는
     * 다른 트랜잭션의 UPDATE 결과는 0행이 된다. JPA는 이를 동시 수정 충돌로
     * 판단하고 낙관적 락 예외를 발생시킨다.
     *
     * version의 초기화와 증가는 JPA가 담당하므로 애플리케이션에서 직접
     * 값을 설정하거나 증가시키면 안 된다.
     */
    @Version
    private Long version;


    public Product(String name, int price, int stockQuantity) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = ProductStatus.ACTIVE;
    }

    public static Product create(String name, int price, int stockQuantity) {
        return new Product(name, price, stockQuantity);
    }

    /**
     * 재고 감소 매서드
     * 재고감소는 product의 책임이기 때문에
     * 서비스로직에서 생성하면 안된다. setter x
     */
    public void decreaseStock() {
        if(stockQuantity <= 0) {
            throw new IllegalStateException("재고가 부족합니다");
        }
        stockQuantity--;
    }

    /**
     * 재고 증가 매서드
     * product의 책임이기 때문에
     * 서비스로직에서 생성하면 안된다. setter x
     */
    public void increaseStock() {
        stockQuantity++;
    }
}
