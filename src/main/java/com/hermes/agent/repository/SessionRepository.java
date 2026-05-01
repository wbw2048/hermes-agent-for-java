package com.hermes.agent.repository;

import com.hermes.agent.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话 Repository。
 */
@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    /**
     * 按创建时间倒序列出所有会话。
     */
    List<SessionEntity> findAllByOrderByUpdatedAtDesc();
}
