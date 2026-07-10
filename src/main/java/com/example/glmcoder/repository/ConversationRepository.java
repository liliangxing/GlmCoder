package com.example.glmcoder.repository;

import com.example.glmcoder.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByProjectIdOrderByUpdatedAtDesc(String projectId);

    @Query("SELECT c FROM Conversation c WHERE c.projectId = :projectId AND (" +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "c.id IN (SELECT m.conversationId FROM ConversationMessage m WHERE LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%'))))" +
           " ORDER BY c.updatedAt DESC")
    List<Conversation> searchByKeyword(@Param("projectId") String projectId, @Param("keyword") String keyword);
}
