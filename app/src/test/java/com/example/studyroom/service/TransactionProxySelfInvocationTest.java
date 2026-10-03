package com.example.studyroom.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TransactionProxySelfInvocationTest.SelfInvocationService.class)
class TransactionProxySelfInvocationTest {

    @Autowired
    private SelfInvocationService service;

    @Test
    void externalCallPassesThroughTransactionalProxy() {
        // 외부 호출은 Spring Bean 앞의 프록시를 거치므로 inner()의 @Transactional이 적용된다.
        assertTrue(service.inner());
    }

    @Test
    void selfInvocationBypassesTransactionalProxy() {
        // self-invocation: 같은 객체 내부 호출은 프록시를 다시 거치지 않아 트랜잭션이 시작되지 않는다.
        assertFalse(service.outer());
    }

    @Test
    void transactionalOuterStartsTransactionBeforeSelfInvocation(){
        assertTrue(service.transactionalOuter());
    }

    static class SelfInvocationService {

        public boolean outer() {
            // inner()의 애노테이션은 그대로 있지만, this.inner() 호출을 프록시가 가로챌 수 없다.
            return inner();
        }

        @Transactional
        public boolean inner() {
            return TransactionSynchronizationManager
                    .isActualTransactionActive();
        }

        @Transactional
        public boolean transactionalOuter() {
            return inner();
        }
    }
}
