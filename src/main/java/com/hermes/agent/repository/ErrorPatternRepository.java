package com.hermes.agent.repository;

import com.hermes.agent.entity.ErrorPatternEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 错误模式 Repository。
 */
@Repository
public interface ErrorPatternRepository extends JpaRepository<ErrorPatternEntity, Long> {

    /**
     * 按时间倒序查询最近 50 条错误模式。
     */
    List<ErrorPatternEntity> findTop50ByOrderByOccurredAtDesc();

    /**
     * 查询最近 N 条已提取教训的记录（lessonLearned 不为空）。
     */
    @Query("SELECT e FROM ErrorPatternEntity e WHERE e.lessonLearned IS NOT NULL "
         + "ORDER BY e.occurredAt DESC LIMIT :limit")
    List<ErrorPatternEntity> findRecentLessons(@Param("limit") int limit);

    /**
     * 检测指定工具+错误类型在指定时间之后是否出现过（用于 is_repeat 检测）。
     */
    @Query("SELECT COUNT(e) FROM ErrorPatternEntity e "
         + "WHERE e.toolName = :toolName AND e.errorType = :errorType "
         + "AND e.occurredAt > :since")
    long countRecentByToolAndType(@Param("toolName") String toolName,
                                  @Param("errorType") String errorType,
                                  @Param("since") Instant since);

    /**
     * 按会话查询错误模式，按时间倒序。
     */
    List<ErrorPatternEntity> findBySessionIdOrderByOccurredAtDesc(String sessionId);

    /**
     * 统计各错误类型的出现次数。
     */
    @Query("SELECT e.errorType, COUNT(e) FROM ErrorPatternEntity e GROUP BY e.errorType ORDER BY COUNT(e) DESC")
    List<Object[]> countByErrorType();
}
