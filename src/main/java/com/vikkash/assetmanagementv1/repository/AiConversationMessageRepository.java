package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AiConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, Long> {

    List<AiConversationMessage> findByConversationIdOrderByIdAsc(String conversationId);

    /** One row per conversation the caller owns, most-recent first — powers the chat-history sidebar. */
    @Query("SELECT m FROM AiConversationMessage m WHERE m.id IN (" +
           "  SELECT MAX(m2.id) FROM AiConversationMessage m2 WHERE m2.ownerEmployeeId = :ownerId GROUP BY m2.conversationId" +
           ") ORDER BY m.createdAt DESC")
    List<AiConversationMessage> findLatestPerConversation(String ownerId);

    /** First user message of a conversation — used to synthesize a title for the sidebar. */
    List<AiConversationMessage> findFirst1ByConversationIdAndRoleOrderByIdAsc(String conversationId, String role);
}
