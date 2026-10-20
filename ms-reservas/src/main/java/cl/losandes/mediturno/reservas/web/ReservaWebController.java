package cl.losandes.mediturno.reservas.web;

import cl.losandes.mediturno.reservas.aplicacion.CrearReservaCmd;
import cl.losandes.mediturno.reservas.aplicacion.ReservaService;
import cl.losandes.mediturno.reservas.dominio.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Interfaz web de MediTurno. Es la que se automatiza en la Entrega 2. */
@Controller
public class ReservaWebController {

    private final ReservaService servicio;

    public ReservaWebController(ReservaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping({"/", "/reservas"})
    public String listado(@RequestParam(required = false) String rut, Model model) {
        List<Reserva> reservas = servicio.listar(rut);
        model.addAttribute("reservas", reservas);
        model.addAttribute("rut", rut == null ? "" : rut);
        model.addAttribute("ahora", servicio.ahora());
        return "listado";
    }

    @GetMapping("/reservas/nueva")
    public String formulario(Model model) {
        model.addAttribute("especialidades", Especialidad.values());
        model.addAttribute("canales", Canal.values());
        model.addAttribute("pacientes", servicio.pacientes());
        return "nueva";
    }

    @PostMapping("/reservas")
    public String crear(@RequestParam String rutPaciente,
                        @RequestParam String especialidad,
                        @RequestParam String fechaHoraAtencion,
                        @RequestParam String canal,
                        @RequestParam(required = false) String aceptaListaEspera,
                        RedirectAttributes flash) {
        try {
            LocalDateTime fecha = LocalDateTime.parse(fechaHoraAtencion);
            Reserva creada = servicio.crear(new CrearReservaCmd(rutPaciente.trim(),
                    Especialidad.valueOf(especialidad), fecha, Canal.valueOf(canal),
                    aceptaListaEspera != null));
            flash.addFlashAttribute("exito", "Reserva " + creada.id() + " creada en estado "
                    + creada.estado() + ".");
            return "redirect:/reservas/" + creada.id();
        } catch (DateTimeParseException e) {
            flash.addFlashAttribute("error", "La fecha y hora no tienen un formato válido.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", "Especialidad o canal no válidos.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.codigo() + " · " + e.getMessage());
        }
        return "redirect:/reservas/nueva";
    }

    @GetMapping("/reservas/{id}")
    public String detalle(@PathVariable Long id, Model model, RedirectAttributes flash) {
        return servicio.porId(id).map(reserva -> {
            model.addAttribute("reserva", reserva);
            model.addAttribute("paciente", servicio.pacientes().stream()
                    .filter(p -> p.rut().equals(reserva.rutPaciente())).findFirst().orElse(null));
            model.addAttribute("ahora", servicio.ahora());
            return "detalle";
        }).orElseGet(() -> {
            flash.addFlashAttribute("error", "No existe la reserva " + id + ".");
            return "redirect:/";
        });
    }

    @PostMapping("/reservas/{id}/{accion}")
    public String accion(@PathVariable Long id, @PathVariable String accion,
                         @RequestParam(required = false) String fechaHoraAtencion,
                         RedirectAttributes flash) {
        try {
            switch (accion) {
                case "confirmar" -> servicio.confirmar(id);
                case "iniciar" -> servicio.iniciar(id);
                case "finalizar" -> servicio.finalizar(id);
                case "inasistencia" -> servicio.marcarInasistencia(id);
                case "anular" -> {
                    Reserva anulada = servicio.anular(id);
                    flash.addFlashAttribute("exito", anulada.multa() > 0
                            ? "Reserva anulada con multa de $" + anulada.multa() + "."
                            : "Reserva anulada sin costo.");
                    return "redirect:/reservas/" + id;
                }
                case "modificar" -> servicio.modificar(id, LocalDateTime.parse(fechaHoraAtencion));
                default -> flash.addFlashAttribute("error", "Acción desconocida: " + accion);
            }
            if (!flash.getFlashAttributes().containsKey("error")) {
                flash.addFlashAttribute("exito", "Operación realizada.");
            }
        } catch (DateTimeParseException e) {
            flash.addFlashAttribute("error", "La fecha y hora no tienen un formato válido.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.codigo() + " · " + e.getMessage());
        }
        return "redirect:/reservas/" + id;
    }

    @GetMapping("/pacientes")
    public String pacientes(Model model) {
        model.addAttribute("pacientes", servicio.pacientes());
        return "pacientes";
    }
}
