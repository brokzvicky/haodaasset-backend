package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOrderByDueDateAscCreatedAtDesc();

    List<Task> findByStatusNotOrderByDueDateAsc(String status);

    List<Task> findByStatusOrderByDueDateAsc(String status);

    long countByStatusNot(String status);

    long countByStatus(String status);
}
