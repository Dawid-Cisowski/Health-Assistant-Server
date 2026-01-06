package com.healthassistant.meals;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface MealProjectionJpaRepository extends JpaRepository<MealProjectionJpaEntity, Long> {

    Optional<MealProjectionJpaEntity> findByEventId(String eventId);

    List<MealProjectionJpaEntity> findByDeviceIdAndDateOrderByMealNumberAsc(String deviceId, LocalDate date);

    List<MealProjectionJpaEntity> findByDeviceIdAndDateOrderByMealNumberAsc(String deviceId, LocalDate date, Pageable pageable);

    List<MealProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAscMealNumberAsc(
            String deviceId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(MAX(m.mealNumber), 0) FROM MealProjectionJpaEntity m WHERE m.deviceId = :deviceId AND m.date = :date")
    int findMaxMealNumber(@Param("deviceId") String deviceId, @Param("date") LocalDate date);

    boolean existsByDeviceId(String deviceId);

    boolean existsByDeviceIdAndDate(String deviceId, LocalDate date);

    void deleteByDeviceId(String deviceId);

    void deleteByDeviceIdAndDate(String deviceId, LocalDate date);

    void deleteByEventId(String eventId);
}
