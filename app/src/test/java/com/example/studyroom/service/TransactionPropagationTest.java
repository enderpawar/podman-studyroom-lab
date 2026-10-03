package com.example.studyroom.service;

import com.example.studyroom.domain.Reservation;
import com.example.studyroom.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import({
        TransactionPropagationTest.PropagationOuterService.class,
        TransactionPropagationTest.PropagationInnerService.class
})
class TransactionPropagationTest {

    @Autowired
    private PropagationOuterService outerservice;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void requiresNewSurviesOuterFailure()
    {
        assertThrows(RuntimeException.class,
                () -> outerservice.reserveThenFail("D-101", "진우"));

        assertTrue(reservationRepository.findAll().stream()
                .anyMatch(r-> r.getRoomName().equals("D-101")));
    }

    static class PropagationOuterService{
        private final PropagationInnerService innerService;

        PropagationOuterService(PropagationInnerService innerService){
            this.innerService = innerService;
        }

        @Transactional
        public void reserveThenFail(String roomName, String requesterName){ //1. 트랜잭션 A 시작 (reserveThen Fail의 트랜잭션)
            innerService.reserve(roomName, requesterName); // 2. REQUIRED(기본값) 라면? -> 이미 A가 있으니 새로 안 만들고 A에 합류 (reserve의 트랜잭션은 적용 안됨.)
            throw new RuntimeException("의도적 실패"); // 3. A 안에서 예외 발생 -> A 전체 롤백 ( 이를 방지하기 위해 Propagation.REQUIRES_NEW를 쓴다!! )
        }
    }



    static class PropagationInnerService {
        private final ReservationRepository repository;

        PropagationInnerService(ReservationRepository repository){
            this.repository = repository;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW) // propagation 지정 없음 = 기본값 REQUIRED, 트랜잭션 범위 분리하고 싶으면 propagation 인수 넣어주기
        public Reservation reserve(String roomname, String requestername) { // 이미 Transaction이 있음.
            Reservation reservation = new Reservation(roomname,requestername);
            reservation.confirm();
            return repository.save(reservation);
        } // !! Propagation 쓸때 주의점!  -> 커넥션 풀 ( HikariCP ) 최대 10개인데, 부분 트랜잭션이 살아있게끔 해버리면 커넥션 풀이 꽉 차 deadlock 상태에 진입할 수 있음. 조심해야함.
    }
}