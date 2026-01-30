### Business Impact

Tax management team reports that invalid billing entity addresses are preventing payment processing. The primary issue is **province/state field inconsistencies** - addresses contain incorrect or malformed state/province data that fails downstream validation in tax systems.

**Confirmed Affected Regions:**
- United States
- Canada
- Australia

**Unknown:**
- Example of invalid addresses (anonymized)
- Who is responsible for fixing invalid addresses
- Whether other countries are affected
- Frequency and impact metrics

### Proposed Technical Solution

Integration of Google Address Validation API and Places Autocomplete API to:
1. **Validate addresses on submission** - prevent invalid data from entering system
2. **Provide autocomplete suggestions** - reduce manual entry errors
3. **Standardize address formats** - ensure consistent province/state representation

**Key API Characteristics:**
- **Google Address Validation API**: Returns validation granularity (PREMISE/ROUTE/LOCALITY/UNRECOGNIZED) indicating confidence level
- **Google Places Autocomplete API**: Provides address suggestions as user types
- **Coverage**: Excellent for US/CA/AU (USPS CASS certified for US), variable for other regions
- **Cost**: Autocomplete can be FREE when using session tokens with validation calls

---

## Current State Analysis

### Address Storage Architecture

**Primary Attribute:** `BusinessRegistrationAddress`

Stored as dynamic attribute with hierarchical structure:
```
BusinessRegistrationAddress (ObjectValue)
├── CountryCode (TextValue)
├── State/Province (MultiConditionalObjectValue - country-specific)
├── City (TextValue)
├── Suburb (TextValue)
├── PostalCode (TextValue)
├── Line1 (TextValue)
├── Line2 (TextValue)
└── Region (TextValue)
```

**Storage Mechanism:**
- Attributes stored as `JsonNode` in PostgreSQL JSONB column
- Wrapped in `AttributeValue` sealed interface (types: TextValue, ObjectValue, MultiConditionalObjectValue, ListValue, FileValue)
- Versioned automatically via Hibernate Envers audit trail
- Each attribute has `AttributeVerification` status (Accepted/Pending/Rejected)

**Country-Specific Logic:**

`BusinessRegistrationAddressMapper` handles transformations:
- **US**: State codes ↔ Full names (e.g., "CA" ↔ "California") via `AmericaStates` enum
- **Canada**: Province codes ↔ Names via `CanadaStates` enum
- **Australia**: State abbreviations (NSW, VIC, QLD, etc.)
- **Others**: Generic `Region` field

**File References:**
- `/domain/entity/attribute/attribute-store/model/AttributeValue.java`
- `/domain/entity/attribute/attribute-mapping/mapper/BusinessRegistrationAddressMapper.java`
- `/kyc/legal-entity/model/LegalEntityAddress.java`

---

### Current Validation Architecture

**Validation Flow:**
```
REST Controller (input validation)
    ↓
EntityFacade (transaction coordination)
    ↓
UpdateAttributesValuesUseCase (business logic)
    ↓
AttributeFacade (attribute orchestration)
    ↓
AttributeValidationFacade (validation engine)
    ↓
AttributeValidationService (executes validators)
    ↓
AttributeValidatorRegistry (type-based lookup)
```

**Existing Address Validators:**
- `AmericaStateCodeAttributeValidator` - Validates US state codes against enum
- `CanadaStateCodeAttributeValidator` - Validates Canada provinces against enum
- `RegexAttributeValidator` - Pattern-based validation
- `EnumAttributeValidator` - Enumeration validation

**Validator Interface Pattern:**
```java
interface AttributeValidator<C> {
    C configure(Map<String, Object> parameters);
    boolean nonEmpty(JsonNode value);
    Collection<String> validate(JsonNode value, C definition);
}
```

**Validation Registration:**
- Validators registered in `AttributeValidationModuleConfiguration` via `@Bean` methods
- Lookup by type string (e.g., "US_STATES_CODE_VALIDATOR")
- Configuration parameters passed from attribute definition JSON

**File References:**
- `/domain/entity/attribute/attribute-validation/validator/`
- `/domain/entity/attribute/attribute-validation/configuration/AttributeValidationModuleConfiguration.java`
- `/domain/entity/attribute/attribute-validation/facade/AttributeValidationFacade.java`

---

### External API Integration Patterns

**Existing Pattern Example:** `OnboardingWebRestClient`

```java
@Component
@RequiredArgsConstructor
public class OnboardingWebRestClient {
    private final WebClient.Builder webClientBuilder;
    private final KycLegalEntityConfigurationProperties properties;

    public RequiredKycLevelRestResponse fetchOrgRequiredKycLevel(String orgId) {
        return webClientBuilder.baseUrl(properties.onboardingUrl())
            .build()
            .get()
            .uri(uriBuilder -> ...)
            .retrieve()
            .onStatus(HttpStatusCode::isError, ...)
            .bodyToMono(RequiredKycLevelRestResponse.class)
            .block();
    }
}
```

**Pattern Characteristics:**
- WebClient-based (reactive but blocking via `.block()`)
- Configuration via `@ConfigurationProperties`
- Error handling via `onStatus(HttpStatusCode::isError, ...)`
- Custom exceptions for API failures
- Registered as `@Component` beans

**File References:**
- `/kyc/legal-entity/status/OnboardingWebRestClient.java`
- `/domain/entity/eligibility/definition/snapshot/EligibilityDefinitionSnapshotRestWebClient.java`

---

## Affected Components

### 1. Attribute Storage Layer

**Impact:** Address attributes need to store validation metadata

**Current Gap:**
- No field to store validation status (PREMISE/ROUTE/LOCALITY/etc.)
- No timestamp for when validation occurred
- No way to distinguish user-provided vs Google-validated addresses

**Questions:**
- Store validation metadata in existing `BusinessRegistrationAddress` attribute?
- Create separate `ValidatedBusinessRegistrationAddress` attribute?
- Store only metadata (validation status, timestamp) separately?
- Should Google's formatted address overwrite user input or coexist?

**Compliance Considerations:**
- All changes must be auditable (Envers already handles this)
- Soft deletes only (regulatory requirement)
- PII must not leak to logs (`@ToString.Exclude`)

---

### 2. Validation Architecture

**Impact:** New validator needed for Google API integration

**Integration Point:** Create validator implementing `AttributeValidator<GoogleAddressConfig>`

**Considerations:**
- External API call during validation (latency impact)
- Error handling when Google API unavailable
- Caching strategy for repeated validations
- Rate limiting to avoid quota exhaustion
- How to handle validation granularity levels (accept/warn/reject)

**Questions:**
- What granularity level should block submission? (UNRECOGNIZED only? Or also LOCALITY?)
- Should validation be mandatory or optional based on country coverage quality?
- Fallback behavior when Google API fails?
- Should existing state code validators run in addition to Google validation?

---

### 3. Address Mapping Logic

**Impact:** Reconcile Google's address format with entity attribute schema

**Current Logic:**
- `BusinessRegistrationAddressMapper` performs country-specific transformations
- Handles bidirectional mapping (e.g., "California" ↔ "CA")
- Supports `MultiConditionalObjectValue` for country-specific fields

**Google API Format:**
- Returns `administrativeArea` (standardized province/state)
- Returns `formattedAddress` (Google's canonical representation)
- May use different conventions than internal enums

**Questions:**
- Does Google's `administrativeArea` align with existing `AmericaStates`/`CanadaStates` enums?
- Should mapper normalize Google response to match existing schema?
- How to handle discrepancies (e.g., Google returns "California", schema expects "CA")?
- Need bidirectional mapping: User input → Google → Entity schema?

---

### 4. REST API Surface

**Impact:** Decision on API contract changes

**Current Endpoints:**
```
POST /entity/{entityType}/{entityReference}/synchronize
  → Updates entity attributes (including addresses)

GET /entity/{entityType}/{entityReference}
  → Retrieves entity with attributes
```

**Options:**

**Option A: Transparent Validation (No API Changes)**
- Validation happens automatically when `synchronize` endpoint called
- Errors returned via existing `ErrorRestResponse` structure
- Pros: No breaking changes, seamless integration
- Cons: Cannot pre-validate, slower response time, no autocomplete support

**Option B: Explicit Validation Endpoints**
```
GET /address/autocomplete?input={text}&country={code}
  → Return address suggestions

POST /address/validate
  → Explicit validation before entity update
```
- Pros: Frontend can show autocomplete, pre-validate before submission, better UX
- Cons: API contract expansion, frontend changes required

**Questions:**
- Should validation be transparent (Option A) or explicit (Option B)?
- Does autocomplete need to be real-time (frontend calls Google) or proxied through backend?
- How to handle session tokens for cost optimization (frontend vs backend responsibility)?

---

### 5. Transaction Management

**Impact:** External API calls within transaction boundaries

**Current Pattern:**
```java
public EntityWithData updateEntity(...) {
    return transactionExecutor.runInTransaction(() -> {
        // All database operations here
        return result;
    });
}
```

**Considerations:**
- Google API call is external, not part of database transaction
- Long-running external calls extend transaction duration (connection pool pressure)
- Retry logic for transient Google API failures
- What happens if validation succeeds but entity update fails (rollback)?

**Questions:**
- Should Google API be called inside transaction or before?
- Acceptable transaction duration with external API call?
- Retry/timeout configuration?
- Circuit breaker pattern needed?

---

### 6. Error Handling

**Impact:** New error scenarios for invalid addresses and API failures

**Current Pattern:**
- `@ControllerAdvice` handlers per domain module
- `ErrorRestResponse` envelope with `List<ErrorRestModel>`
- Errors include: code, message, context map

**New Error Scenarios:**
1. **Invalid Address** - User-provided address unrecognizable by Google
2. **Ambiguous Address** - Multiple possible matches
3. **Partial Match** - Address validated only to LOCALITY/ROUTE level
4. **Google API Failure** - External service unavailable
5. **Quota Exhaustion** - API rate limits exceeded

**Questions:**
- What error messages should be user-facing?
- Should Google's suggested corrections be returned in error context?
- How to differentiate "address invalid" vs "validation service down"?
- What HTTP status codes for each scenario?
- Manual override workflow for edge cases?

---

### 7. Audit & Compliance

**Impact:** Validation events must be auditable for regulatory compliance

**Current Audit Trail:**
- Hibernate Envers tracks all attribute changes
- `@CreatedDate`, `@LastModifiedDate` on entities
- Audit records never deleted (regulatory requirement)
- PII excluded from logs via `@ToString.Exclude`

**New Audit Requirements:**
- Log validation requests (original address, validated address, outcome)
- Track who requested validation (`X-Requested-By` header)
- Record validation granularity level
- Preserve Google's suggested corrections

**Questions:**
- Separate audit table for validation events or embed in attribute audit?
- How long to retain validation audit records?
- Should failed validation attempts be logged?
- Privacy considerations for storing Google's responses (contains PII)?

---

## Open Design Questions

### 1. Backward Compatibility

**Question:** How to handle existing addresses without validation?

**Options:**
- **Lazy validation** - Validate only on next update
- **Background job** - Validate existing addresses proactively
- **Report-only** - Identify invalid addresses but don't block

**Considerations:**
- 10,000+ existing billing entities with addresses
- Google API cost for bulk validation
- Risk of invalidating currently-working addresses
- Manual correction workflow for failures

---

### 2. Validation Strictness

**Question:** What validation granularity level should prevent submission?

**Granularity Levels:**
- **PREMISE/SUB_PREMISE** - Exact building match (highest confidence)
- **ROUTE** - Street-level match (missing building number)
- **LOCALITY** - City-level match (street not found)
- **OTHER/UNRECOGNIZED** - No meaningful match (lowest confidence)

**Considerations:**
- US/CA/AU have excellent coverage (can require PREMISE)
- Other countries have variable coverage (may need to accept LOCALITY)
- Rural addresses often validate only to ROUTE/LOCALITY
- New developments may not be in Google's database

**Country-Specific Thresholds?**
- US: Require PREMISE (CASS certified)
- Canada: Require PREMISE
- Australia: Require PREMISE
- Brazil: Accept LOCALITY (fair coverage)
- India: Accept LOCALITY (fair coverage)

---

### 3. Data Model Changes

**Question:** How to store validated addresses and metadata?

**Schema Options:**

**A) Enhance Existing Attribute**
```json
{
  "BusinessRegistrationAddress": {
    "CountryCode": "US",
    "State": "CA",
    "City": "Mountain View",
    // ... existing fields

    "GoogleValidationStatus": "PREMISE",
    "GoogleValidationTimestamp": "2026-01-22T10:30:00Z",
    "GoogleFormattedAddress": "..."
  }
}
```

**B) Separate Validated Attribute**
```json
{
  "BusinessRegistrationAddress": { /* original user input */ },
  "ValidatedBusinessRegistrationAddress": { /* Google's version */ }
}
```

**C) Metadata-Only Attribute**
```json
{
  "BusinessRegistrationAddress": { /* user's address (possibly corrected) */ },
  "AddressValidationMetadata": {
    "Provider": "Google",
    "Granularity": "PREMISE",
    "Timestamp": "..."
  }
}
```

---

### 4. Integration Approach

**Question:** When should validation occur?

**Timing Options:**

**A) Synchronous (During Entity Update)**
- Validate as part of `synchronize` endpoint
- Pros: Immediate feedback, prevents invalid data
- Cons: Slower response time, external dependency in critical path

**B) Asynchronous (Post-Save)**
- Save address first, validate in background
- Pros: Fast response, doesn't block user
- Cons: Invalid data enters system temporarily, eventual consistency

**C) Pre-Validation (Before Entity Update)**
- Frontend calls validation endpoint first
- Pros: Fast entity update, better UX with autocomplete
- Cons: Two API calls, frontend complexity

---

### 5. Autocomplete Integration

**Question:** How to integrate Google Places Autocomplete?

**Architectural Questions:**
- Should autocomplete be client-side (frontend calls Google directly) or server-side proxy?
- How to manage session tokens for cost optimization?
- Debounce timing (how long after user stops typing)?
- Minimum characters before querying (3? 5?)?
- How to filter results (only street addresses, exclude POIs)?

**Cost Implications:**
- **With session tokens**: Autocomplete FREE when followed by validation call
- **Without session tokens**: $0.00283 per autocomplete request
- Monthly usage: ~1,500 addresses → $7.50 with tokens vs $50 without

---

### 6. Failure Handling

**Question:** What should happen when Google API is unavailable?

**Fallback Options:**
- **Fail open** - Accept address without validation (data quality risk)
- **Fail closed** - Reject submission (user impact)
- **Fallback validators** - Use existing state code validators only
- **Manual override** - Allow CSR team to bypass validation

**Monitoring:**
- Google API availability/latency metrics
- Validation success rate by country
- Quota usage tracking
- Alert thresholds

---

## Coverage Quality by Region

Google Address Validation API support varies significantly by region:

| Region | Quality | Notes |
|--------|---------|-------|
| **USA** | ✅ Excellent | CASS certified, ~100% coverage |
| **Canada** | ✅ Excellent | Urban/suburban excellent, rural good |
| **Australia** | ✅ Excellent | Postal database integration |
| **UK** | ✅ Excellent | Royal Mail integration |
| **Western Europe** | ✅ Good | Germany, France, Spain very good |
| **Japan** | ✅ Good | Urban excellent, rural variable |
| **Brazil** | ⚠️ Fair | Major cities good, rural poor |
| **India** | ⚠️ Fair | Metro areas good, inconsistent elsewhere |
| **Eastern Europe** | ⚠️ Variable | Poland/Czech good, others mixed |
| **Rural Areas** | ❌ Poor | Especially developing countries |
| **New Developments** | ❌ Poor | 6+ months lag for new construction |

**Implication:** Validation strictness may need to be country-dependent based on coverage quality.

---

## Architectural Principles to Maintain

The implementation must adhere to entity-manager architectural patterns:

✅ **TransactionExecutor pattern** - Never use `@Transactional`, use `TransactionExecutor` interface
✅ **Constructor injection** - `@RequiredArgsConstructor`, no field `@Autowired`
✅ **Bean registration** - Via `@Configuration` classes, not component scanning
✅ **Audit trail** - Hibernate Envers tracks all changes (regulatory requirement)
✅ **Soft deletes** - Archive flags, never hard delete user data
✅ **Validator registry** - Pluggable via `AttributeValidatorRegistry`
✅ **Error envelopes** - `ErrorRestResponse` with `List<ErrorRestModel>`
✅ **Security** - No PII in logs, parameterized queries only
✅ **Immutability** - Records, `@Value` classes, `final` fields preferred

**File Reference:** `/CLAUDE.md` - Entity Manager Development Guide

---

## Technical Constraints

### Google API Specifics

**Authentication:**
- API Key (server-side, IP-restricted for backend)
- OR Service Account credentials
- Separate frontend API key (HTTP referrer-restricted) for autocomplete

**Rate Limits:**
- No published hard limit, but billing-based throttling
- Recommended: 10 requests/second for sustained usage

**Pricing:**
- Address Validation: $0.005 per request
- Autocomplete: $0.00283 per request (FREE with session tokens + validation)
- Monthly free tier: $200 credit (~40,000 validations)

**Dependencies:**
- Spring WebFlux (WebClient) - already present in project
- Optional: `com.google.maps:google-maps-services` SDK

---

## Scope of Changes Summary

| Component | Impact | Complexity |
|-----------|--------|------------|
| **Attribute Storage** | Schema extension for validation metadata | Low (JSON flexible) |
| **Validation Framework** | New validator + registration | Medium (external API) |
| **Address Mapper** | Google format normalization | Medium (country-specific) |
| **REST API** | Optional new endpoints for autocomplete | Low-Medium |
| **Transaction Mgmt** | External API in transaction flow | Medium (latency/retry) |
| **Error Handling** | New error types and messages | Low |
| **Audit Trail** | Validation event logging | Low (Envers exists) |
| **Configuration** | API keys, timeouts, thresholds | Low |
| **Monitoring** | Metrics, alerts, dashboards | Medium |

**Overall Complexity:** Medium - Well-defined integration pattern, but external dependency and country-specific logic add complexity.

---

## Questions for Architectural Review

1. **Data Model**: Single attribute with metadata (Option A) vs separate validated attribute (Option B)?

2. **Validation Timing**: Synchronous during entity update vs asynchronous post-save vs frontend pre-validation?

3. **API Contract**: Keep existing endpoints (transparent validation) vs add new autocomplete/validate endpoints?

4. **Strictness**: Country-specific granularity thresholds or uniform global threshold?

5. **Backward Compatibility**: Lazy validation on update vs background job for existing addresses?

6. **Failure Mode**: Fail open (accept without validation) vs fail closed (reject) when Google API down?

7. **Autocomplete**: Client-side (frontend calls Google) vs server-side proxy?

8. **Cost Optimization**: Session token management responsibility (frontend vs backend)?

9. **Manual Override**: CSR workflow for edge cases (new developments, Google coverage gaps)?

10. **Migration Strategy**: Big bang vs gradual country-by-country rollout?

---

## Next Steps

1. **Architectural decision** on open questions above
2. **API key provisioning** (Google Cloud project setup)
3. **Design session** to finalize data model and API contract
4. **Proof of concept** with US addresses to validate integration pattern
5. **Implementation planning** based on agreed design

---

## References

### Jira Context
- **Comment ID 122181**: Technical analysis and API recommendations
- **Parent Epic PM-28623**: Address Validation for Billing Entities

### Codebase References
- `/CLAUDE.md` - Development guide and architectural patterns
- `/domain/entity/attribute/` - Attribute storage and validation framework
- `/kyc/legal-entity/model/` - Address models and country-specific enums

### External Documentation
- [Google Address Validation API](https://developers.google.com/maps/documentation/address-validation/overview)
- [Google Places Autocomplete](https://developers.google.com/maps/documentation/places/web-service/autocomplete)
- [Coverage Details](https://developers.google.com/maps/documentation/address-validation/coverage)

---

**Document Status:** Ready for architectural review
**Author:** Software Architect Analysis
**Next Review:** After architectural decisions made