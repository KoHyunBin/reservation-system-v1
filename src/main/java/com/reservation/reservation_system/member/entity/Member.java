package com.reservation.reservation_system.member.entity;

import com.reservation.reservation_system.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    public Member(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public static Member create(String name, String email) {
        return new Member(name, email);
    }


}
