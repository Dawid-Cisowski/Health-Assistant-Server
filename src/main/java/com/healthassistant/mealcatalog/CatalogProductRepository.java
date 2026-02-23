package com.healthassistant.mealcatalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long> {

    Optional<CatalogProduct> findByDeviceIdAndNormalizedTitle(String deviceId, String normalizedTitle);

    @Query("""
            SELECT p FROM CatalogProduct p
            WHERE p.deviceId = :deviceId
            AND LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY p.usageCount DESC
            """)
    List<CatalogProduct> searchByDeviceIdAndQuery(@Param("deviceId") String deviceId,
                                                  @Param("query") String query,
                                                  Pageable pageable);

    List<CatalogProduct> findByDeviceIdOrderByUsageCountDesc(String deviceId, Pageable pageable);
}
