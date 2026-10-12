package com.agent.dock.notification.repository;

import com.agent.dock.notification.domain.NotificationChannel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    List<NotificationChannel> findAllByOrderByIdDesc();

    /** 그 프로젝트 채널 + 전역 채널(project_id null). */
    List<NotificationChannel> findByProjectIdIsNullOrProjectIdOrderByIdDesc(Long projectId);
}