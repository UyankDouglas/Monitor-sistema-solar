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

    /**
     * Resumo de um período para consolidação diária: integração
     * tempo-ponderada das potências (kWh), pico de geração com horário,
     * contador diário do inversor e nº de amostras.
     *
     * <p>O intervalo entre amostras consecutivas é limitado a 60 s
     * ({@code least(dt, 60)}): se o sistema ficou fora do ar, o buraco não é
     * extrapolado como se a última potência tivesse se mantido.</p>
     *
     * <p>O contador diário vem da ÚLTIMA amostra não nula do dia — nunca do
     * {@code max()}: logo após a meia-noite o polling ainda pode entregar o
     * contador final de ontem carimbado com timestamp de hoje, e um max()
     * congelaria esse valor stale como a geração do dia inteiro.</p>
     *
     * <p>Retorno (1 linha): [consumption_kwh, export_kwh, import_kwh,
     * generation_integrated_kwh, peak_power_w, peak_at, min_gen_power_w,
     * daily_counter_kwh, sample_count]</p>
     */
    @Query(value = """
            with s as (
                select sampled_at, ac_power_w, load_power_w, export_power_w, import_power_w,
                       daily_energy_kwh,
                       least(coalesce(extract(epoch from
                             (lead(sampled_at) over (order by sampled_at) - sampled_at)), 0), 60) as dt
                from energy_sample
                where inverter_id = :inverterId
                  and sampled_at >= :from and sampled_at < :to
            )
            select coalesce(sum(load_power_w   * dt), 0) / 3600000.0  as consumption_kwh,
                   coalesce(sum(export_power_w * dt), 0) / 3600000.0  as export_kwh,
                   coalesce(sum(import_power_w * dt), 0) / 3600000.0  as import_kwh,
                   coalesce(sum(ac_power_w     * dt), 0) / 3600000.0  as generation_integrated_kwh,
                   max(ac_power_w)                                    as peak_power_w,
                   (select s2.sampled_at from s s2
                    where s2.ac_power_w is not null
                    order by s2.ac_power_w desc, s2.sampled_at asc limit 1) as peak_at,
                   min(ac_power_w) filter (where ac_power_w > 0)      as min_gen_power_w,
                   (select s3.daily_energy_kwh from s s3
                    where s3.daily_energy_kwh is not null
                    order by s3.sampled_at desc limit 1)              as daily_counter_kwh,
                   count(*)                                           as sample_count
            from s
            """, nativeQuery = true)
    List<Object[]> daySummary(@Param("inverterId") Long inverterId,
                              @Param("from") Instant from,
                              @Param("to") Instant to);
}
