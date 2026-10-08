package ch.swisstopo.monteis.core.modules.organisation.web;

import ch.swisstopo.monteis.core.infrastructure.csv.CsvWriter;
import ch.swisstopo.monteis.core.modules.organisation.domain.Organisation;
import ch.swisstopo.monteis.core.modules.organisation.service.OrganisationService;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.inbound.WriteOrganisationDto;
import ch.swisstopo.monteis.core.modules.organisation.web.dto.outbound.OrganisationResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organisations")
public class OrganisationController {
  private final OrganisationService service;
  private final OrganisationWebMapper mapper;

  public OrganisationController(OrganisationService service, OrganisationWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @Operation(
      summary = "Get all organisations",
      description = "Retrieves the list of organisations, sorted alphabetically by name.")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved organisations")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<OrganisationResponseDto>> getOrganisations() {
    return ResponseEntity.ok(service.findAllOrganisations().stream().map(mapper::toDto).toList());
  }

  @Operation(
      summary = "Download organisations as CSV",
      description = "Streams all organisations, sorted alphabetically by name, as a CSV file.")
  @ApiResponse(
      responseCode = "200",
      description = "Successfully streamed organisations as CSV",
      content = @Content(mediaType = "text/csv"))
  @GetMapping(value = "/csv", produces = "text/csv")
  public void getOrganisationsCsv(HttpServletResponse response) throws IOException {
    response.setContentType("text/csv");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Content-Disposition", "attachment; filename=\"organisations.csv\"");

    Writer writer =
        new BufferedWriter(
            new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
    CsvWriter.writeRow(writer, List.of("id", "name"));
    for (Organisation organisation : service.findAllOrganisations()) {
      CsvWriter.writeRow(writer, List.of(organisation.getId(), organisation.getName()));
    }
    writer.flush();
  }

  @Operation(summary = "Get an organisation by id", description = "Retrieves an organisation by id")
  @ApiResponse(responseCode = "200", description = "Successfully retrieved organisation")
  @GetMapping(path = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OrganisationResponseDto> getOrganisation(@PathVariable UUID id) {
    return ResponseEntity.ok(mapper.toDto(service.getOrganisation(id)));
  }

  @Operation(
      summary = "Create a new organisation",
      description = "Adds an organisation to the list. The name must be unique, ignoring case.")
  @ApiResponse(responseCode = "201", description = "Organisation successfully created")
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OrganisationResponseDto> createOrganisation(
      @Valid @RequestBody WriteOrganisationDto dto) {
    Organisation created = service.createOrganisation(mapper.toDomain(dto));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(created));
  }

  @Operation(
      summary = "Delete an organisation",
      description = "Removes the organisation from the list and from every experiment.")
  @ApiResponse(responseCode = "204", description = "Organisation successfully deleted")
  @DeleteMapping("{id}")
  public ResponseEntity<Void> deleteOrganisation(@PathVariable UUID id) {
    service.deleteOrganisation(id);
    return ResponseEntity.noContent().build();
  }
}
