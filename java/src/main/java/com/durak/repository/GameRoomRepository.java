package com.durak.repository;

import com.durak.model.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
    GameRoom findByRoomCode(String roomCode);
}
