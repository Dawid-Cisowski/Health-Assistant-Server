package com.healthassistant.medicalexams;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "examination_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExaminationLink {

    @EmbeddedId
    private ExaminationLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idA")
    private Examination examinationA;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idB")
    private Examination examinationB;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static ExaminationLink create(Examination a, Examination b) {
        var link = new ExaminationLink();
        if (a.getId().toString().compareTo(b.getId().toString()) < 0) {
            link.id = new ExaminationLinkId(a.getId(), b.getId());
            link.examinationA = a;
            link.examinationB = b;
        } else {
            link.id = new ExaminationLinkId(b.getId(), a.getId());
            link.examinationA = b;
            link.examinationB = a;
        }
        link.createdAt = Instant.now();
        return link;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    static class ExaminationLinkId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "id_a", nullable = false)
        private UUID idA;

        @Column(name = "id_b", nullable = false)
        private UUID idB;

        ExaminationLinkId(UUID idA, UUID idB) {
            this.idA = idA;
            this.idB = idB;
        }
    }
}
