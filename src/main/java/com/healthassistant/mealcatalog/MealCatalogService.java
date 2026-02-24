package com.healthassistant.mealcatalog;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.mealcatalog.api.MealCatalogFacade;
import com.healthassistant.mealcatalog.api.dto.CatalogProductResponse;
import com.healthassistant.mealcatalog.api.dto.SaveProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
class MealCatalogService implements MealCatalogFacade {

    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final CatalogProductRepository repository;

    @Override
    public void saveProduct(String deviceId, SaveProductRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            log.warn("Skipping catalog save: empty title for device {}", SecurityUtils.maskDeviceId(deviceId));
            return;
        }

        String normalizedTitle = CatalogProduct.normalizeTitle(request.title());
        log.debug("Saving product to catalog for device {}: {}",
                SecurityUtils.maskDeviceId(deviceId), SecurityUtils.sanitizeForLog(request.title()));

        boolean succeeded = IntStream.range(0, MAX_RETRY_ATTEMPTS)
                .mapToObj(attempt -> attemptUpsert(deviceId, normalizedTitle, request, attempt))
                .filter(Boolean::booleanValue)
                .findFirst()
                .orElse(false);

        if (!succeeded) {
            log.warn("Failed to save catalog product after {} attempts for device {}: {}",
                    MAX_RETRY_ATTEMPTS, SecurityUtils.maskDeviceId(deviceId),
                    SecurityUtils.sanitizeForLog(request.title()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean attemptUpsert(String deviceId, String normalizedTitle, SaveProductRequest request, int attempt) {
        try {
            var existing = repository.findByDeviceIdAndNormalizedTitle(deviceId, normalizedTitle);
            existing.ifPresentOrElse(
                    product -> {
                        product.recordUsage(
                                request.mealType(),
                                request.caloriesKcal(),
                                request.proteinGrams(),
                                request.fatGrams(),
                                request.carbohydratesGrams(),
                                request.healthRating()
                        );
                        repository.save(product);
                        log.debug("Updated catalog product '{}' for device {}, usage count: {}",
                                SecurityUtils.sanitizeForLog(request.title()),
                                SecurityUtils.maskDeviceId(deviceId),
                                product.getUsageCount());
                    },
                    () -> {
                        var product = new CatalogProduct(
                                deviceId,
                                request.title(),
                                request.mealType(),
                                request.caloriesKcal(),
                                request.proteinGrams(),
                                request.fatGrams(),
                                request.carbohydratesGrams(),
                                request.healthRating()
                        );
                        repository.save(product);
                        log.debug("Created new catalog product '{}' for device {}",
                                SecurityUtils.sanitizeForLog(request.title()),
                                SecurityUtils.maskDeviceId(deviceId));
                    }
            );
            return true;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Optimistic lock conflict on catalog save, attempt {}/{}", attempt + 1, MAX_RETRY_ATTEMPTS);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogProductResponse> searchProducts(String deviceId, String query, int maxResults) {
        int effectiveLimit = Math.min(Math.max(maxResults, 1), MAX_SEARCH_RESULTS);
        log.debug("Searching catalog for device {}: query='{}', limit={}",
                SecurityUtils.maskDeviceId(deviceId), SecurityUtils.sanitizeForLog(query), effectiveLimit);

        String escapedQuery = escapeLikeWildcards(query);
        return repository.searchByDeviceIdAndQuery(deviceId, escapedQuery, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogProductResponse> getTopProducts(String deviceId, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_SEARCH_RESULTS);
        log.debug("Fetching top {} catalog products for device {}", effectiveLimit, SecurityUtils.maskDeviceId(deviceId));

        return repository.findByDeviceIdOrderByUsageCountDesc(deviceId, PageRequest.of(0, effectiveLimit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CatalogProductResponse toResponse(CatalogProduct product) {
        return new CatalogProductResponse(
                product.getTitle(),
                product.getMealType(),
                product.getCaloriesKcal(),
                product.getProteinGrams(),
                product.getFatGrams(),
                product.getCarbohydratesGrams(),
                product.getHealthRating(),
                product.getUsageCount(),
                product.getLastUsedAt()
        );
    }

    private static String escapeLikeWildcards(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
