CREATE TABLE flag
(
    id        BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    date TIMESTAMP,
    player_id BIGINT,
    color VARCHAR(10),
    CONSTRAINT pk_flag PRIMARY KEY (id),
    FOREIGN KEY(player_id) REFERENCES player(id)
);
