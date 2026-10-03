package com.example.studyroom.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundExceptionBecomes404Response() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new ReservationNotFoundException(7L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("예약을 찾을 수 없습니다. (id: 7)", response.getBody().get("error"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() {
        String internalMessage = "jdbc:password=secret";

        ResponseEntity<Map<String, String>> response =
                handler.handleUnexpected(new RuntimeException(internalMessage));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("요청 처리 중 오류가 발생했습니다.", response.getBody().get("error"));
        assertFalse(response.getBody().toString().contains(internalMessage));
    }
}
