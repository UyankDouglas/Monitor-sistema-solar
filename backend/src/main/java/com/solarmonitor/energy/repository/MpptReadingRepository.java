package com.solarmonitor.energy.repository;

import com.solarmonitor.energy.domain.MpptReading;
import com.solarmonitor.energy.domain.MpptReadingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MpptReadingRepository extends JpaRepository<MpptReading, MpptReadingId> {

    /** Leituras de todas as strings MPPT de uma amostra específica. */
    List<MpptReading> findAllById_InverterIdAndId_SampledAtOrderById_StringIndex(Long inverterId, Instant sampledAt);

    /** Série de uma string MPPT no intervalo [from, to). */
    @Query("""
            select r from MpptReading r
            where r.id.inverterId = :inverterId
              and r.id.stringIndex = :stringIndex
              and r.id.sampledAt >= :from and r.id.sampledAt < :to
            order by r.id.sampledAt
            """)
    List<MpptReading> findSeries(@Param("inverterId") Long inverterId,
                                 @Param("stringIndex") Short stringIndex,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);
}
