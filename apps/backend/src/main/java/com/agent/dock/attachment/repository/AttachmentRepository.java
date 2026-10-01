package com.agent.dock.attachment.repository;

import com.agent.dock.attachment.domain.Attachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTaskIdOrderByIdAsc(Long taskId);

    List<Attachment> findByIdInOrderByIdAsc(List<Long> ids);

    /** 태스크를 지우기 전에 첨부 기록을 먼저 지운다(FK 안전). */
    @Modifying
    @Query("delete from Attachment a where a.taskId in :taskIds")
    void deleteByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);
}
