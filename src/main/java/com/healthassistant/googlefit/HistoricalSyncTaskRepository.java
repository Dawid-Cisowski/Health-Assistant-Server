package com.healthassistant.googlefit;

import com.healthassistant.googlefit.HistoricalSyncTask.SyncTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface HistoricalSyncTaskRepository extends JpaRepository<HistoricalSyncTask, Long> {

    List<HistoricalSyncTask> findTop10ByStatusOrderByCreatedAtAsc(SyncTaskStatus status);

    boolean existsBySyncDateAndStatusIn(LocalDate syncDate, List<SyncTaskStatus> statuses);
}
