package com.reservation.reservation_system.global;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 공통 컬럼을 상속받기 위한 jpa 기능 - entity은 테이블 테이블은 만들지 않고 자식 엔티티에게 컬럼은 물려준다
@EntityListeners(AuditingEntityListener.class) // 생성일 수정일 자동으로 관리
public abstract class BaseTimeEntity {

    @CreatedDate
    private LocalDateTime createdAt;
}
