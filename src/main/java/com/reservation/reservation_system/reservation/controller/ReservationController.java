package com.reservation.reservation_system.reservation.controller;

import com.reservation.reservation_system.reservation.dto.ReservationCreateRequest;
import com.reservation.reservation_system.reservation.dto.ReservationResponse;
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
}
