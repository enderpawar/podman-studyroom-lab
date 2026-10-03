package com.example.studyroom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

// Podman 실험 — H2 MySQL 모드가 아니라 진짜 MySQL 8 컨테이너에서 Flyway 마이그레이션 전체를 실행한다.
// Day32의 "--주석" 1064 오류처럼 H2는 통과하고 MySQL에서만 터지는 문법 오류를 로컬에서 잡기 위한 테스트.
//
// disabledWithoutDocker = true: Docker API 소켓(Docker 또는 Podman)을 못 찾으면 실패가 아니라 skip.
// 그래서 컨테이너 엔진이 없는 PC에서도 ./gradlew test 의 기존 71개는 그대로 통과한다.
// 컨테이너 엔진 연결은 DOCKER_HOST 등 환경변수로 준다(README의 Testcontainers 절 참고).
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FlywayMySqlIntegrationTest {

    // @ServiceConnection: 컨테이너의 JDBC URL·계정으로 JdbcConnectionDetails Bean을 등록한다.
    // DataSource 자동 구성은 이 Bean이 있으면 spring.datasource.* 프로퍼티(= build.gradle.kts가
    // 강제한 SPRING_DATASOURCE_URL=H2 환경변수 포함)보다 이 Bean을 우선한다.
    //
    // 이미지 이름은 Dockerfile처럼 정식 이름(docker.io/library/mysql:8)을 쓴다. 다만 문자열을 그대로
    // new MySQLContainer<>("docker.io/library/mysql:8") 로 넘기면 Testcontainers가 기본 이름 'mysql'과
    // 다른 이미지로 보고 생성자에서 IllegalStateException("... is a compatible substitute for 'mysql'")을
    // 던진다(1.21.2에서 확인). 그래서 같은 이미지라고 명시적으로 선언한다.
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(
            DockerImageName.parse("docker.io/library/mysql:8").asCompatibleSubstituteFor("mysql"));

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dataSourceIsMySqlNotH2() throws Exception {
        // Test 태스크의 H2 환경변수를 @ServiceConnection이 실제로 덮어썼는지 먼저 확인한다.
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    void allFlywayMigrationsSucceedOnRealMySql() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 0", Integer.class);

        assertEquals(0, failed);
    }
}
