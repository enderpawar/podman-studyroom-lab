package com.example.studyroom.service;

import com.example.studyroom.exception.ReservationNotFoundException;
import com.example.studyroom.repository.InMemoryReservationRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationServiceTest {

    @Test
    void cancelUpdatesExistingReservationWithoutAddingDuplicate() {
        InMemoryReservationRepository repository = new InMemoryReservationRepository();
        ReservationService service = new ReservationService(repository);
        Long id = service.reserve("A-101", "민지").getId();
        String cancelReason = "일정이 안맞음";
        service.cancel(id,cancelReason);

        assertEquals(1, repository.findAll().size());
        assertFalse(repository.findById(id).orElseThrow().isConfirmed());
    }

    @Test
    void cancelThrowsDomainExceptionWhenReservationDoesNotExist() {
        ReservationService service = new ReservationService(new InMemoryReservationRepository());
        assertThrows(ReservationNotFoundException.class, () -> service.cancel(999L,"일정이 맞지 않습니다 "));
    }
}
