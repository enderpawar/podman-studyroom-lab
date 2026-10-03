CREATE TABLE member (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        name VARCHAR(50) NOT NULL,
                        PRIMARY KEY (id)
);

ALTER TABLE reservation
    ADD member_id BIGINT;

ALTER TABLE reservation
    ADD CONSTRAINT fk_reservation_member
        FOREIGN KEY (member_id) REFERENCES member(id);