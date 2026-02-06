package com.healthassistant.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface FcmTokenRepository extends JpaRepository<FcmTokenEntity, Long> {

    Optional<FcmTokenEntity> findByDeviceId(String deviceId);

    @Query("SELECT t FROM FcmTokenEntity t WHERE t.active = true")
    List<FcmTokenEntity> findAllActive();
}
