package com.healthassistant.meals;

import java.util.List;

class CatalogProductNotFoundException extends RuntimeException {

    private final List<Long> missingIds;

    CatalogProductNotFoundException(List<Long> missingIds) {
        super("Catalog product(s) not found: " + missingIds);
        this.missingIds = missingIds;
    }

    List<Long> getMissingIds() {
        return missingIds;
    }
}
