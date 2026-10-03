package com.example.studyroom.repository;

import com.example.studyroom.domain.Reservation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest // 1. 진짜  Spring 컨테이너를 통째로 띄운다. ( mock 아님. 실제 DB까지 붙는 통합 테스트)
@Transactional // 이 클래스의 각 테스트 메서드를 트랜잭션 하나로 감싼다
class JpaReservationRepositoryTest {

    @Autowired
    private ReservationRepository repository; // Spring이 실제 Repository 구현체를 주입한다 ( Spring이 실제 빈을 넣어주는 것)

    @Autowired
    private EntityManager entityManager; // 영속성 컨텍스트를 직접 조작할 도구

    @Test
    void savesAndFindsReservationThroughJpaAdapter() {
        Reservation reservation = new Reservation("A101", "jinwoo");
        reservation.confirm();

        Reservation saved = repository.save(reservation);
        entityManager.flush();
        entityManager.clear();

        Reservation found = repository.findById(saved.getId()).orElseThrow();

        assertNotNull(saved.getId());
        assertEquals("A101", found.getRoomName());
        assertEquals("jinwoo", found.getRequesterName());
        assertTrue(found.isConfirmed());
    }

    @Test
    void savingExistingReservationUpdatesWithoutAddingDuplicate() {
        int countBeforeSave = repository.findAll().size();
        Reservation reservation = new Reservation("B202", "minji");
        reservation.confirm();
        Reservation saved = repository.save(reservation);
        entityManager.flush();

        String cancelReason = "일정이 맞지 않습니다.";
        saved.cancel(cancelReason);
        repository.save(saved);
        entityManager.flush();
        entityManager.clear();

        List<Reservation> reservations = repository.findAll();
        List<Reservation> savedIdRows = reservations.stream()
                .filter(candidate -> candidate.getId().equals(saved.getId()))
                .toList();

        assertEquals(countBeforeSave + 1, reservations.size());
        assertEquals(1, savedIdRows.size());
        assertFalse(savedIdRows.get(0).isConfirmed());
    }

    @Test
    void repeatedFindByIdWithinSameTransactionReturnsSameInstance() {
        // 1. 예약 하나 저장하고 id 확보 (기존 테스트 참고)
        Reservation reservation = new Reservation("B101", "Jinwoo");
        Reservation saved = repository.save(reservation);
        entityManager.flush();
        Long id = saved.getId();

        entityManager.clear();

        // 2. 같은 id로 두 번 조회 — 여기서는 clear() 호출 금지!
        Reservation first = repository.findById(id).orElseThrow();
        Reservation second = repository.findById(id).orElseThrow();

        // 3. 두 참조가 정말 같은 객체인지 확인
        assertSame(first, second);
    }

    @Test
    void modifyingManagedEntityWithoutExplicitSaveStillPersistsOnFlush() {
        // 1. 예약 하나 저장하고 flush로 DB에 반영
        Reservation reservation = new Reservation("B202","노은주");
        reservation.confirm();

        Reservation saved = repository.save(reservation);
        entityManager.flush();
        Long id = saved.getId();
        Reservation managed = repository.findById(id).orElseThrow();

        // 2. 같은 트랜잭션 안에서 다시 조회 (캐시에서 나오든 DB에서 나오든 상관없음)



        String cancelReason = "일정이 맞지 않습니다.";
        // 3. save()는 절대 호출하지 않고, 상태만 바꾼다
        managed.cancel(cancelReason);   // 예: cancel()

        // 4. 여기서 명시적으로 flush를 호출해 변경 감지를 강제로 트리거
        entityManager.flush();

        // 5. clear()로 캐시를 비우고 DB에서 새로 읽어와서 실제 반영됐는지 확인
        entityManager.clear();
        Reservation reloaded = repository.findById(id).orElseThrow();

        assertFalse(reloaded.isConfirmed());  // isConfirmed() 등, cancel()로 바뀐 상태를 확인
    }

    @Test
    void checkConstraintRejectsEmptyRoomName() {
        assertThrows(PersistenceException.class, () -> {
            entityManager.createNativeQuery(
                            "INSERT INTO reservation (room_name, requester_name, confirmed) VALUES ('', ?, false)")
                    .setParameter(1, "jinwoo")
                    .executeUpdate();
            entityManager.flush();
        });
    }
    @Test
    void checkCancelReason(){
        Reservation reservation = new Reservation("B101","노은주");
        reservation.cancel("임시 이유1");

        Reservation saved = repository.save(reservation);
        entityManager.flush();
        Long id = saved.getId();
        Reservation managed = repository.findById(id).orElseThrow();

        managed.cancel("임시 이유2");

        entityManager.flush();

        entityManager.clear();
        Reservation reloaded = repository.findById(id).orElseThrow();

        assertEquals(reloaded.getCancelReason(),saved.getCancelReason());
    }
}
