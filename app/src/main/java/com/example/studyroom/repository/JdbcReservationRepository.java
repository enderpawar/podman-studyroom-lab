package com.example.studyroom.repository;

import com.example.studyroom.domain.Reservation;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcReservationRepository implements ReservationRepository{
//DataSource = 커넥션 풀.  SpringBoot 가 aplication.yml의 url/username/passwrd를 읽어 HikariCP 풀 객체를 Bean으로 만들어 두었고, 우리는 그걸 주입받기만 한다.
    //매번 접속하지 않는 이유? -> TCP 연결 + 인증은 수십 ms 짜리 비용이라 미리 열어둔 커넥션을 빌렸다 반납하는 편이 압도적으로 싸다.
    private final DataSource dataSource;

    public JdbcReservationRepository(DataSource dataSource){ // 생성자
        this.dataSource = dataSource;
    }

    @Override
    public Reservation save(Reservation reservation){
        //값을 문자열로 이어붙이지 말고 ?로 자리만 비워둔다.
        // DB는 "SQL 구조"를 먼저 파싱해 굳혀두고, 그 뒤에 값을 채워준다.
        // 그래서 값에 무엇이 들어오든 구조를 바꿀 수 없다.-> SQL Injection 공격 방어 가능
        String sql = "INSERT INTO reservation (room_name, requester_name, confirmed) VALUES (?,?,?)";

        //try-with resource: 소괄호 안에서 연 자원은 블록을 벗어날 때 예외가 났든 안 났든 자동으로 close() 된다.
        //커넥션을 반납하지 않으면 풀이 말라(쓸 수 있는 공간이 사라져) 서버 전체가 멈춘다.
        try(Connection con = dataSource.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reservation.getRoomName()); // 1부터 시작한다 (0 아님)
            ps.setString(2, reservation.getRequesterName());
            ps.setBoolean(3, reservation.isConfirmed());
            ps.executeUpdate(); //영향받은 행 수를 돌려준다

            try(ResultSet keys = ps.getGeneratedKeys()){
                if(keys.next()){
                    reservation.assignId(keys.getLong(1));
                }
            }
            return reservation;
        } catch (SQLException e){ //SQLException은 checked 예외라 안잡으면 컴파일 안됨.
            // 원인 e를 반드시 넘겨야 스택트레이스가 보존된다.
            throw new IllegalStateException("예약 저장 실패", e);
        }
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        String sql = "SELECT id, room_name, requester_name, confirmed FROM reservation WHERE id = ?";


        try (Connection con = dataSource.getConnection();
        PreparedStatement ps = con.prepareStatement(sql)) {
            // 1. id를 1번 자리에 바인딩(Long이므로 set String x)
            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();

            } catch (SQLException e) {
                throw new IllegalStateException("예약 조회에 실패했습니다.", e);

            }

        }catch (SQLException e){
            throw new IllegalStateException("예약 조회에 실패했습니다.",e);
        }
    }

    @Override
    public List<Reservation> findAll(){
        String sql = "SELECT id, room_name, requester_name, confirmed FROM reservation";
        List<Reservation> result = new ArrayList<>();

        try(Connection con = dataSource.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()){

            while(rs.next()){
                result.add(mapRow(rs));
            }
            return result;
        } catch(SQLException e) {
            throw new IllegalStateException("예약 목록 조회에 실패했습니다", e);
        }
    }

    private Reservation mapRow(ResultSet rs) throws SQLException{
        // 1단계: 생성자로 만든다. 생성자가 받는건 문자열 두개뿜.
        Reservation reservation = new Reservation(rs.getString("room_name"),rs.getString("requester_name")); // SELECT 목록에 있는 컬럼명을 가져올때 "" 붙여줘야함
        // 2단계: DB가 붙여준 id를 심는다. 생성자로는 못넣으니 별도 메서드.
        reservation.assignId(rs.getLong("id"));

        if(rs.getBoolean("confirmed")){
            reservation.confirm();
        }

        return reservation;

    }
}

