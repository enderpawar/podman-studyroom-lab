package com.example.studyroom.service;

import com.example.studyroom.domain.Reservation;
import com.example.studyroom.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(ReservationServiceTransactionTest.RollbackScenarioService.class)
public class ReservationServiceTransactionTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RollbackScenarioService rollbackScenarioService;

    @Test
    void cancelCommitsChangedState() {
        Reservation saved = reservationService.reserve("C-101","진우");

        reservationService.cancel(saved.getId(),"일정 변경");

        Reservation found = reservationRepository
                .findById(saved.getId())
                .orElseThrow();

        assertFalse(found.isConfirmed());
        assertEquals("일정 변경",found.getCancelReason());
    }

    @Test
    void runtimeExceptionRollsBackChangedState() {
        Reservation saved = reservationService.reserve("C-102","민지");

        assertThrows(
                RuntimeException.class,() -> rollbackScenarioService.cancelThenFail(saved.getId())
        );

        Reservation found = reservationRepository
                .findById(saved.getId())
                .orElseThrow();

        assertTrue(found.isConfirmed());
        assertNull(found.getCancelReason());
    }

    static class RollbackScenarioService {
        private final ReservationRepository repository;
        private final EntityManager entityManager;

        RollbackScenarioService(
                ReservationRepository repository,
                EntityManager entityManager
        ) {
            this.repository = repository;
            this.entityManager = entityManager;
        }

        @Transactional
        public void cancelThenFail(Long id) {
            Reservation reservation =
                    repository.findById(id).orElseThrow();

            reservation.cancel("강제 실패");
            entityManager.flush(); // UPDATE를 DB에 전송하지만 커밋은 x

            throw new RuntimeException("취소 처리 중 실패");
        }
    }
    }
