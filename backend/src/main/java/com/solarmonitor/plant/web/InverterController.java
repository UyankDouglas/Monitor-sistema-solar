package com.solarmonitor.plant.web;

import com.solarmonitor.plant.repository.InverterRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inverters")
@RequiredArgsConstructor
@Tag(name = "Inversores", description = "Inversores cadastrados e seus estados")
public class InverterController {

    private final InverterRepository inverterRepository;
    private final InverterDtoMapper mapper;

    @GetMapping
    @Operation(summary = "Lista todos os inversores")
    @Transactional(readOnly = true)
    public List<InverterDto> list() {
        return mapper.toDtos(inverterRepository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe de um inversor")
    @Transactional(readOnly = true)
    public InverterDto get(@PathVariable Long id) {
        return inverterRepository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Inversor " + id + " não encontrado"));
    }
}
