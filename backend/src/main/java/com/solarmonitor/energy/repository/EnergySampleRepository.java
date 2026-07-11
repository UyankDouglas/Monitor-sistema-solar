package com.solarmonitor.energy.repository;

import com.solarmonitor.energy.domain.EnergySample;
import com.solarmonitor.energy.domain.EnergySampleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EnergySampleRepository extends JpaRepository<EnergySample, EnergySampleId> {

    /** Leitura mais recente de um inversor (card "Potência Atual"). */
    Optional<EnergySample> findFirstById_InverterIdOrderById_SampledAtDesc(Long inverterId);

    /** Série temporal de um inversor no intervalo [from, to). */
    @Query("""
            select s from EnergySample s
            where s.id.inverterId = :inverterId
              and s.id.sampledAt >= :from and s.id.sampledAt < :to
            order by s.id.sampledAt
            """)
    List<EnergySample> findSeries(@Param("inverterId") Long inverterId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    /**
     * Downsampling no banco via time_bucket do TimescaleDB — média por janela
     * de {@code bucketSeconds}. Usado nos gráficos de períodos longos, onde
     * retornar toda amostra de 5 s seria proibitivo.
     * Retorno: [bucket Instant, avg ac_power_w, avg load_power_w, avg battery_soc_pct].
     */
    @Query(value = """
            select time_bucket(:bucketSeconds * interval '1 second', s.sampled_at) as bucket,
                   avg(s.ac_power_w)      as avg_ac_power_w,
                   avg(s.load_power_w)    as avg_load_power_w,
                   avg(s.battery_soc_pct) as avg_battery_soc_pct
            from energy_sample s
            where s.inverter_id = :inverterId
              and s.sampled_at >= :from and s.sampled_at < :to
            group by bucket
            order by bucket
            """, nativeQuery = true)
    List<Object[]> findSeriesDownsampled(@Param("inverterId") Long inverterId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to,
                                         @Param("bucketSeconds") int bucketSeconds);
}
