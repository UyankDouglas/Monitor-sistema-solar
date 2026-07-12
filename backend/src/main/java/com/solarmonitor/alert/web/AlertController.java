package com.solarmonitor.alert.web;

import com.solarmonitor.alert.domain.AlertStatus;
import com.solarmonitor.alert.repository.AlertRepository;
import com.solarmonitor.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alertas", description = "Ocorrências e ciclo de vida (reconhecer/resolver)")
public class AlertController {

    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final AlertDtoMapper mapper;

    @GetMapping
    @Operation(summary = "Alertas paginados, mais recentes primeiro",
            description = "Filtro opcional por status (ACTIVE, ACKNOWLEDGED, RESOLVED).")
    @Transactional(readOnly = true)
    public PageResponse<AlertDto> list(
            @RequestParam(name = "status", required = false) AlertStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        // PageRequest primeiro: valida page/size (400 consistente) antes de
        // qualquer aritmética de índice nos dois ramos.
        PageRequest pageRequest = PageRequest.of(page, size);
        if (status != null) {
            List<AlertDto> filtered = mapper.toDtos(
                    alertRepository.findAllByStatusOrderByTriggeredAtDesc(status));
            int fromIdx = (int) Math.min(pageRequest.getOffset(), filtered.size());
            int toIdx = Math.min(fromIdx + size, filtered.size());
            return new PageResponse<>(filtered.subList(fromIdx, toIdx), filtered.size(),
                    (int) Math.ceil(filtered.size() / (double) size), page, size);
        }
        Page<AlertDto> result = alertRepository.findAllByOrderByTriggeredAtDesc(pageRequest)
                .map(mapper::toDto);
        return PageResponse.from(result);
    }

    /**
     * Envelope de paginação próprio: serializar PageImpl direto emite aviso
     * do Spring Data e o formato é declarado instável entre versões — este
     * record congela o contrato consumido pelo frontend.
     */
    public record PageResponse<T>(List<T> content, long totalElements,
                                  int totalPages, int number, int size) {
        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getTotalElements(),
                    page.getTotalPages(), page.getNumber(), page.getSize());
        }
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Reconhece um alerta ativo")
    public AlertDto acknowledge(@PathVariable Long id) {
        return mapper.toDto(alertService.acknowledge(id));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve um alerta")
    public AlertDto resolve(@PathVariable Long id) {
        return mapper.toDto(alertService.resolve(id));
    }
}
