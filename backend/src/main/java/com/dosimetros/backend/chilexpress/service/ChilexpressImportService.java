package com.dosimetros.backend.chilexpress.service;

import com.dosimetros.backend.chilexpress.entity.ChilexpressOt;
import com.dosimetros.backend.chilexpress.model.ArchivoChilexpress;
import com.dosimetros.backend.chilexpress.model.FilaChilexpress;
import com.dosimetros.backend.chilexpress.repository.ChilexpressOtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra/actualiza Órdenes de Transporte desde un export de Chilexpress.
 *
 * Upsert con "ventana de tiempo": una OT existente se ACTUALIZA solo si fue
 * vista hace poco (dentro de VENTANA_DIAS respecto al periodo del archivo).
 * Si el número de OT reaparece tras un gap largo, es una reutilización de
 * Chilexpress y se registra como una OT NUEVA, conservando el historial.
 */
@Service
public class ChilexpressImportService {

    /** Ventana para considerar "el mismo envío" (días). Mayor que la carga semanal
     *  y del orden del ciclo de purga de Chilexpress (~3 meses). */
    static final long VENTANA_DIAS = 100;

    private final ChilexpressOtRepository repo;
    private final ChilexpressReaderService reader;

    public ChilexpressImportService(ChilexpressOtRepository repo, ChilexpressReaderService reader) {
        this.repo = repo;
        this.reader = reader;
    }

    @Transactional
    public Map<String, Integer> importar(String empresa, MultipartFile file) throws IOException {
        ArchivoChilexpress arch = reader.leer(file);
        LocalDateTime ahora = LocalDateTime.now();
        int nuevas = 0, actualizadas = 0;

        for (FilaChilexpress f : arch.filas) {
            List<ChilexpressOt> previos = repo.findByEmpresaAndNroOtOrderByCreadoEnDesc(empresa, f.nroOt);
            ChilexpressOt existente = elegirParaActualizar(previos, arch.periodoDesde);
            if (existente != null) {
                actualizar(existente, f, arch, ahora);
                repo.save(existente);
                actualizadas++;
            } else {
                repo.save(crear(empresa, f, arch, ahora));
                nuevas++;
            }
        }

        Map<String, Integer> resumen = new LinkedHashMap<>();
        resumen.put("nuevas", nuevas);
        resumen.put("actualizadas", actualizadas);
        resumen.put("total", arch.filas.size());
        return resumen;
    }

    /** Devuelve la OT existente a actualizar, o null si corresponde una nueva. */
    private ChilexpressOt elegirParaActualizar(List<ChilexpressOt> previos, LocalDate periodoDesdeNuevo) {
        if (previos == null || previos.isEmpty()) return null;
        ChilexpressOt reciente = previos.get(0); // el más reciente (orden por creadoEn desc)
        LocalDate visto = reciente.getPeriodoHasta() != null
                ? reciente.getPeriodoHasta()
                : reciente.getCreadoEn().toLocalDate();
        LocalDate ref = periodoDesdeNuevo != null ? periodoDesdeNuevo : LocalDate.now();
        long gap = ChronoUnit.DAYS.between(visto, ref); // negativo si el nuevo periodo solapa/precede
        return gap <= VENTANA_DIAS ? reciente : null;
    }

    private void actualizar(ChilexpressOt o, FilaChilexpress f, ArchivoChilexpress arch, LocalDateTime ahora) {
        // Solo estado y recepción (los datos fijos no cambian).
        o.setEstado(f.estado);
        o.setOficinaDestino(f.oficinaDestino);
        o.setReceptor(f.receptor);
        o.setRutReceptor(f.rutReceptor);
        o.setFechaPrimerIntento(f.fechaPrimerIntento);
        o.setFechaEntrega(f.fechaEntrega);
        o.setHoraEntrega(f.horaEntrega);
        o.setCertificadoEntrega(f.certificadoEntrega);
        o.setPeriodoHasta(maxFecha(o.getPeriodoHasta(), arch.periodoHasta)); // rastrea "última vez visto"
        o.setActualizadoEn(ahora);
    }

    private ChilexpressOt crear(String empresa, FilaChilexpress f, ArchivoChilexpress arch, LocalDateTime ahora) {
        ChilexpressOt o = new ChilexpressOt();
        o.setEmpresa(empresa);
        o.setNroOt(f.nroOt);
        o.setOtPadre(f.otPadre);
        o.setNroReferencia(f.nroReferencia);
        o.setNombreDestinatario(f.nombreDestinatario);
        o.setDestino(f.destino);
        o.setDireccion(f.direccion);
        o.setServicio(f.servicio);
        o.setValorDeclarado(f.valorDeclarado);
        o.setOficinaOrigen(f.oficinaOrigen);
        o.setTipoAdmision(f.tipoAdmision);
        o.setTipoEntrega(f.tipoEntrega);
        o.setEstado(f.estado);
        o.setOficinaDestino(f.oficinaDestino);
        o.setReceptor(f.receptor);
        o.setRutReceptor(f.rutReceptor);
        o.setFechaPrimerIntento(f.fechaPrimerIntento);
        o.setFechaEntrega(f.fechaEntrega);
        o.setHoraEntrega(f.horaEntrega);
        o.setCertificadoEntrega(f.certificadoEntrega);
        o.setPeriodoDesde(arch.periodoDesde);
        o.setPeriodoHasta(arch.periodoHasta);
        o.setCreadoEn(ahora);
        o.setActualizadoEn(ahora);
        return o;
    }

    private static LocalDate maxFecha(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
