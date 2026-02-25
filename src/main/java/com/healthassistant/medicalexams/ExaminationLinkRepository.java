package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExaminationLinkRepository extends JpaRepository<ExaminationLink, ExaminationLink.ExaminationLinkId> {

    @Query("SELECT l FROM ExaminationLink l " +
           "JOIN FETCH l.examinationA a JOIN FETCH a.examType " +
           "JOIN FETCH l.examinationB b JOIN FETCH b.examType " +
           "WHERE a.id = :examId OR b.id = :examId")
    List<ExaminationLink> findAllLinksForExamination(@Param("examId") UUID examId);

    @Query("SELECT l FROM ExaminationLink l WHERE l.examinationA.id = :idA AND l.examinationB.id = :idB")
    Optional<ExaminationLink> findLinkByOrderedIds(@Param("idA") UUID idA, @Param("idB") UUID idB);
}
