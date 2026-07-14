package com.reservation.reservation_system.reservation.controller;

import com.reservation.reservation_system.common.exception.GlobalExceptionHandler;
import com.reservation.reservation_system.common.exception.reservation.ReservationErrorCode;
import com.reservation.reservation_system.common.exception.reservation.ReservationException;
import com.reservation.reservation_system.reservation.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(controllers = ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    @DisplayName("예약 취소 성공")
    void cancel_success() throws Exception {
        mockMvc.perform(delete("/api/reservations/{reservationId}/cancel",1L))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("이미 취소된 예약을 다시 취소하면 409와 예외 메시지를 반환한다")
    void cancel_fail_when_already_canceled() throws Exception{

        //given
        Long reservationId = 1L;

        willThrow(
                new ReservationException(
                        ReservationErrorCode.ALREADY_CANCELLED
                )).given(reservationService).cancel(reservationId);

        // when & then
        mockMvc.perform(
                delete("/api/reservations/{reservationId}/cancel", reservationId)
        ).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("R003"))
                .andExpect(jsonPath("$.message").value("이미 취소된 예약입니다."));
    }
}