package com.solarmonitor.common.web;

import com.solarmonitor.plant.domain.Inverter;
import com.solarmonitor.plant.repository.InverterRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve o inversor alvo dos endpoints: id explícito ou, na instalação
 * mono-inversor (caso do usuário), o primeiro cadastrado quando omitido.
 */
@Component
@RequiredArgsConstructor
public class InverterResolver {

    private final InverterRepository inverterRepository;

    public Inverter resolve(Long inverterId) {
        if (inverterId != null) {
            return inverterRepository.findById(inverterId).orElseThrow(
                    () -> new EntityNotFoundException("Inversor " + inverterId + " não encontrado"));
        }
        return inverterRepository.findAll().stream().findFirst().orElseThrow(
                () -> new EntityNotFoundException("Nenhum inversor cadastrado"));
    }
}
