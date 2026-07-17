package com.reservation.reservation_system.reservation.controller;

import com.reservation.reservation_system.reservation.dto.response.ReservationDetailResponse;
import com.reservation.reservation_system.reservation.dto.request.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.dto.response.ReservationResponse;
import com.reservation.reservation_system.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.reserve(request);
    }

    @DeleteMapping("/{reservationId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long reservationId) {
        reservationService.cancel(reservationId);
        return ResponseEntity.noContent().build();
    }

    //예약 단건 조회
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationDetailResponse> findReservation(@PathVariable Long reservationId) {
        ReservationDetailResponse response = reservationService.findReservation(reservationId);
        return ResponseEntity.ok(response);
    }
}
