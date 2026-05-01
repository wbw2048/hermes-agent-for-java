package com.hermes.agent.repository;

import com.hermes.agent.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息 Repository。
 */
@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    /**
     * 按会话 ID 和顺序索引升序获取消息。
     */
    List<MessageEntity> findBySessionIdOrderByOrderIndexAsc(String sessionId);

    /**
     * 统计会话中的消息数量。
     */
    long countBySessionId(String sessionId);

    /**
     * 删除会话的全部消息。
     */
    void deleteBySessionId(String sessionId);
}
