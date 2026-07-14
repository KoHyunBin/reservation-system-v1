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
