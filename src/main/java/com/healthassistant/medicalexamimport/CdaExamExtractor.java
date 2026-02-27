package com.healthassistant.medicalexamimport;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
@Slf4j
class CdaExamExtractor {

    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String ICD9_SYSTEM_NAME = "ICD-9-PL";
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter CDA_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CDA_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private record RawEntry(String icd9Code, String examTypeCode, String sectionTitle, List<ExtractedResultData> results) {}

    ExtractedExamData extract(byte[] cdaBytes) {
        try {
            Document doc = parseXml(cdaBytes);
            LocalDate collectionDate = extractCollectionDate(doc);
            Instant performedAt = extractPerformedAt(doc);
            String laboratory = extractLaboratory(doc);
            List<ExtractedExamData.ExtractedSectionData> sections = buildSections(doc);

            log.info("CDA extracted: date={}, performedAt={}, laboratory={}, sections={}",
                    collectionDate, performedAt, laboratory, sections.size());

            return ExtractedExamData.valid(collectionDate, performedAt, laboratory, null,
                    sections, BigDecimal.ONE, "CDA_IMPORT");
        } catch (Exception e) {
            log.error("CDA parsing failed: {}", SecurityUtils.sanitizeForLog(e.getMessage()), e);
            return ExtractedExamData.invalid("CDA parsing failed", BigDecimal.ZERO);
        }
    }

    private Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        // Standard JAXP attributes — work on all compliant implementations
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception e) {
            log.debug("DTD blocking feature not supported by XML implementation: {}", e.getMessage());
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    private LocalDate extractCollectionDate(Document doc) {
        return nodeListToStream(doc.getElementsByTagNameNS("*", "encompassingEncounter"))
                .findFirst()
                .flatMap(enc -> directChildren(enc, "effectiveTime").findFirst())
                .map(et -> parseDate(et.getAttribute("value")))
                .orElseGet(() -> extractDocumentDate(doc));
    }

    private Instant extractPerformedAt(Document doc) {
        return nodeListToStream(doc.getElementsByTagNameNS("*", "encompassingEncounter"))
                .findFirst()
                .flatMap(enc -> directChildren(enc, "effectiveTime").findFirst())
                .map(et -> parseInstant(et.getAttribute("value")))
                .orElse(null);
    }

    private LocalDate extractDocumentDate(Document doc) {
        return directChildren(doc.getDocumentElement(), "effectiveTime")
                .findFirst()
                .map(et -> parseDate(et.getAttribute("value")))
                .orElse(null);
    }

    private String extractLaboratory(Document doc) {
        return nodeListToStream(doc.getElementsByTagNameNS("*", "representedOrganization"))
                .findFirst()
                .flatMap(org -> nodeListToStream(org.getElementsByTagNameNS("*", "name"))
                        .findFirst()
                        .map(n -> n.getTextContent().trim()))
                .orElse(null);
    }

    private List<ExtractedExamData.ExtractedSectionData> buildSections(Document doc) {
        // Group by ICD-9 code to preserve individual test identity (e.g. VIT_B12 vs VIT_D as separate exams)
        Map<String, List<ExtractedResultData>> grouped = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, String> examTypes = new LinkedHashMap<>();

        nodeListToStream(doc.getElementsByTagNameNS("*", "entry"))
                .filter(e -> "COMP".equals(e.getAttribute("typeCode")))
                .map(this::parseEntry)
                .filter(Objects::nonNull)
                .forEach(raw -> {
                    grouped.merge(raw.icd9Code(), raw.results(), (existing, incoming) -> {
                        var merged = new java.util.ArrayList<>(existing);
                        merged.addAll(incoming);
                        return merged;
                    });
                    titles.putIfAbsent(raw.icd9Code(), raw.sectionTitle());
                    examTypes.putIfAbsent(raw.icd9Code(), raw.examTypeCode());
                });

        return grouped.entrySet().stream()
                .map(e -> new ExtractedExamData.ExtractedSectionData(
                        examTypes.get(e.getKey()),
                        stripIcd9Suffix(titles.get(e.getKey())),
                        null, null,
                        e.getValue()))
                .toList();
    }

    private String stripIcd9Suffix(String title) {
        if (title == null) return null;
        return title.replaceAll("(?i)\\s*+\\(ICD-9:[^)]*+\\)\\s*+", "").trim();
    }

    private RawEntry parseEntry(Element entry) {
        Optional<Element> outerObsOpt = directChildren(entry, "observation").findFirst();
        if (outerObsOpt.isEmpty()) return null;
        Element outerObs = outerObsOpt.get();

        Optional<Element> codeElOpt = directChildren(outerObs, "code").findFirst();
        if (codeElOpt.isEmpty()) return null;
        Element codeEl = codeElOpt.get();

        // Only process ICD-9-PL top-level observations
        String codeSystemName = codeEl.getAttribute("codeSystemName");
        if (!ICD9_SYSTEM_NAME.equals(codeSystemName)) return null;

        String icd9Code = codeEl.getAttribute("code");
        String sectionTitle = codeEl.getAttribute("displayName");
        String examTypeCode = CdaIcd9ExamTypeMapper.resolve(icd9Code);

        // Structure A: single-value observation — value is directly on outer observation
        // (e.g. Albumina, Wit B12, Krew utajona, Testosteron, TSH, etc.)
        if (directChildren(outerObs, "value").findFirst().isPresent()) {
            ExtractedResultData result = parseMarker(outerObs, examTypeCode, 0);
            if (result == null) return null;
            return new RawEntry(icd9Code, examTypeCode, sectionTitle, List.of(result));
        }

        // Structure B: multi-marker section — values in entryRelationships
        // (e.g. Morfologia, Mocz, Lipidogram, etc.)
        var innerObservations = directChildren(outerObs, "entryRelationship")
                .filter(er -> "COMP".equals(er.getAttribute("typeCode")))
                .flatMap(er -> directChildren(er, "observation").findFirst().stream())
                .toList();

        List<ExtractedResultData> results = IntStream.range(0, innerObservations.size())
                .mapToObj(i -> parseMarker(innerObservations.get(i), examTypeCode, i))
                .filter(Objects::nonNull)
                .toList();

        return new RawEntry(icd9Code, examTypeCode, sectionTitle, results);
    }

    private ExtractedResultData parseMarker(Element markerObs, String examTypeCode, int sortOrder) {
        Optional<Element> codeElOpt = directChildren(markerObs, "code").findFirst();
        if (codeElOpt.isEmpty()) return null;

        String displayName = codeElOpt.get().getAttribute("displayName");
        if ("-".equals(displayName) || displayName.isBlank()) return null;

        Optional<String> markerCodeOpt = CdaMarkerNameMapper.resolve(displayName, examTypeCode);
        if (markerCodeOpt.isEmpty()) return null;
        String markerCode = markerCodeOpt.get();

        BigDecimal valueNumeric = null;
        String unit = null;
        String valueText = null;

        Optional<Element> valueElOpt = directChildren(markerObs, "value").findFirst();
        if (valueElOpt.isPresent()) {
            Element ve = valueElOpt.get();
            String xsiType = resolveXsiType(ve);
            if ("PQ".equals(xsiType)) {
                String valStr = ve.getAttribute("value");
                unit = ve.getAttribute("unit");
                if (!valStr.isBlank()) {
                    try {
                        valueNumeric = new BigDecimal(valStr);
                    } catch (NumberFormatException ex) {
                        valueText = valStr;
                    }
                }
            } else if ("ST".equals(xsiType)) {
                String text = ve.getTextContent().trim();
                valueText = (text.isBlank() || ",".equals(text)) ? "-" : text;
            }
        }

        if (valueNumeric == null && valueText == null) {
            valueText = "-";
        }

        BigDecimal refLow = null;
        BigDecimal refHigh = null;
        Optional<Element> refRangeOpt = directChildren(markerObs, "referenceRange").findFirst();
        if (refRangeOpt.isPresent()) {
            Optional<Element> ivlPqOpt = nodeListToStream(
                    refRangeOpt.get().getElementsByTagNameNS("*", "value"))
                    .filter(e -> "IVL_PQ".equals(resolveXsiType(e)))
                    .findFirst();
            if (ivlPqOpt.isPresent()) {
                refLow = parseRefBound(ivlPqOpt.get(), "low");
                refHigh = parseRefBound(ivlPqOpt.get(), "high");
            }
        }

        String refRangeText = (refLow != null && refHigh != null)
                ? refLow.toPlainString() + " - " + refHigh.toPlainString() : null;

        return new ExtractedResultData(
                markerCode, displayName, examTypeCode,
                valueNumeric, unit, valueNumeric, unit, false,
                refLow, refHigh, refRangeText,
                valueText, sortOrder);
    }

    private BigDecimal parseRefBound(Element ivlPq, String boundName) {
        return directChildren(ivlPq, boundName)
                .findFirst()
                .map(el -> el.getAttribute("value"))
                .filter(v -> !v.isBlank())
                .map(v -> {
                    try {
                        return new BigDecimal(v);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private String resolveXsiType(Element element) {
        String type = element.getAttributeNS(XSI_NS, "type");
        if (type.isBlank()) {
            type = element.getAttribute("xsi:type");
        }
        return type;
    }

    private Stream<Element> directChildren(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        return IntStream.range(0, children.getLength())
                .mapToObj(children::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(e -> localName.equals(e.getLocalName()));
    }

    private Stream<Element> nodeListToStream(NodeList nl) {
        return IntStream.range(0, nl.getLength())
                .mapToObj(nl::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.length() < 8) return null;
        try {
            return LocalDate.parse(value.substring(0, 8), CDA_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.length() < 8) return null;
        try {
            String padded = value.length() >= 14 ? value.substring(0, 14) : value.substring(0, 8) + "000000";
            return LocalDateTime.parse(padded, CDA_DATETIME_FORMAT)
                    .atZone(POLAND_ZONE)
                    .toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
