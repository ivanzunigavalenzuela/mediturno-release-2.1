package cl.losandes.mediturno.tarificacion.plomeria;

import cl.losandes.mediturno.tarificacion.api.MsTarificacionApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plomería de ms-tarificacion: el servicio levanta, lee el padrón de convenios y su
 * respuesta trae los ocho campos del desglose.
 *
 * <p>No comprueba ningún monto: eso es materia de la Entrega 1 y del pacto. Acá solo
 * interesa que el servicio esté contestando y que la forma de la respuesta sea la que
 * ms-reservas espera.</p>
 */
@SpringBootTest(classes = MsTarificacionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TarificacionRespondeIT {

    private static final String SOLICITUD = """
            {
              "paciente": {
                "rut": "5.555.555-5",
                "fechaNacimiento": "1986-09-17",
                "prevision": "FONASA",
                "tramoFonasa": "B"
              },
              "especialidad": "MEDICINA_GENERAL",
              "fechaHoraAtencion": "2026-10-05T09:00:00"
            }
            """;

    @Autowired TestRestTemplate cliente;

    @Test
    @DisplayName("el cálculo responde 200 con el desglose completo")
    void desgloseCompleto() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> respuesta = cliente.exchange("/api/tarificacion/calcular",
                HttpMethod.POST, new HttpEntity<>(SOLICITUD, cabeceras), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("\"arancelBase\"")
                .contains("\"recargoUrgencia\"")
                .contains("\"recargoHorarioExtendido\"")
                .contains("\"arancelBruto\"")
                .contains("\"bonificacion\"")
                .contains("\"descuentoAplicado\"")
                .contains("\"montoDescuento\"")
                .contains("\"copago\"");
    }

    @Test
    @DisplayName("el servicio expone su estado de salud")
    void salud() {
        ResponseEntity<String> respuesta = cliente.getForEntity("/actuator/health", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("\"status\":\"UP\"");
    }
}
