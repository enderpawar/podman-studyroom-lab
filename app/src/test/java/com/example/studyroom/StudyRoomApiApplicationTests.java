package com.example.studyroom;

import com.example.studyroom.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring ApplicationContext 기동과 기본 Singleton scope를 검증한다.
 */
@SpringBootTest
class StudyRoomApiApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void reservationServiceBeanIsSingleton() {
        ReservationService first = applicationContext.getBean(ReservationService.class); //Spring 컨테이너에서 ReservationService Bean을 꺼낸다
        ReservationService second = applicationContext.getBean(ReservationService.class); //

        assertSame(first, second);
    }

    @Test
    void reservationServiceIsTransactionalProxy(){
        ReservationService service = applicationContext.getBean(ReservationService.class);

        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));
        assertNotEquals(ReservationService.class,service.getClass());

        System.out.println(service.getClass().getName());
    }
}
