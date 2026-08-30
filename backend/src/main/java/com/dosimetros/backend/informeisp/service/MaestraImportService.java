package com.dosimetros.backend.informeisp.service;

import com.dosimetros.backend.informeisp.entity.IspClienteTecnologia;
import com.dosimetros.backend.informeisp.entity.IspPersonaCodigo;
import com.dosimetros.backend.informeisp.repository.IspClienteTecnologiaRepository;
import com.dosimetros.backend.informeisp.repository.IspPersonaCodigoRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Importa las maestras aprendidas desde un informe ISP ya entregado (.xlsm/.xlsx,
 * hoja DOSIS): RUT -> cod_cargo/cod_prac y (empresa, RUT entidad) -> tecnología.
 * Reconstruye por moda cuando una persona/cliente aparece en varias filas.
 */
@Service
public class MaestraImportService {

    private static final Map<Integer, String> SERV_TEC = new HashMap<>();
    static {
        for (int c : new int[]{3, 4, 5, 6, 7, 43, 44, 45, 46}) SERV_TEC.put(c, "TLD");
        for (int c : new int[]{50, 51, 52, 53, 54, 55}) SERV_TEC.put(c, "OSL");
        SERV_TEC.put(2, "FILM");
    }

    // Índices 0-based en la hoja DOSIS.
    private static final int HEADER_ROW = 5;
    private static final int FIRST_DATA_ROW = 7;
    private static final int C_RUN = 2, C_SERV = 4, C_RUT_ENT = 6, C_PRAC = 8, C_CARGO = 10;

    private final IspPersonaCodigoRepository personaRepo;
    private final IspClienteTecnologiaRepository clienteTecRepo;

    public MaestraImportService(IspPersonaCodigoRepository personaRepo,
                                IspClienteTecnologiaRepository clienteTecRepo) {
        this.personaRepo = personaRepo;
        this.clienteTecRepo = clienteTecRepo;
    }

    @Transactional
    public Map<String, Integer> importar(MultipartFile file, String empresa) throws IOException {
        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = wb.getSheet("DOSIS");
            if (sheet == null) {
                throw new IllegalArgumentException("El archivo no tiene la hoja 'DOSIS'.");
            }
            DataFormatter fmt = new DataFormatter();

            Map<String, Map<Integer, Integer>> cargoCnt = new HashMap<>();
            Map<String, Map<Integer, Integer>> pracCnt = new HashMap<>();
            Map<String, Map<String, Integer>> tecCnt = new HashMap<>();

            for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String run = IspMappings.norm(cell(fmt, row, C_RUN));
                if (run.isEmpty()) continue;

                Integer cargo = intCell(fmt, row, C_CARGO);
                Integer prac = intCell(fmt, row, C_PRAC);
                if (cargo != null) contar(cargoCnt, run, cargo);
                if (prac != null) contar(pracCnt, run, prac);

                Integer serv = intCell(fmt, row, C_SERV);
                String rutEnt = IspMappings.norm(cell(fmt, row, C_RUT_ENT));
                if (serv != null && !rutEnt.isEmpty() && SERV_TEC.containsKey(serv)) {
                    contarStr(tecCnt, rutEnt, SERV_TEC.get(serv));
                }
            }

            int personas = upsertPersonas(cargoCnt, pracCnt);
            int clientes = upsertClientes(empresa, tecCnt);

            Map<String, Integer> resumen = new LinkedHashMap<>();
            resumen.put("personas", personas);
            resumen.put("clientes", clientes);
            return resumen;
        }
    }

    private int upsertPersonas(Map<String, Map<Integer, Integer>> cargoCnt,
                               Map<String, Map<Integer, Integer>> pracCnt) {
        Set<String> ruts = new HashSet<>();
        ruts.addAll(cargoCnt.keySet());
        ruts.addAll(pracCnt.keySet());

        Map<String, IspPersonaCodigo> existentes = new HashMap<>();
        for (IspPersonaCodigo p : personaRepo.findAll()) {
            existentes.put(IspMappings.norm(p.getRut()), p);
        }
        List<IspPersonaCodigo> guardar = new ArrayList<>();
        for (String rut : ruts) {
            Integer cargo = moda(cargoCnt.get(rut));
            Integer prac = moda(pracCnt.get(rut));
            IspPersonaCodigo p = existentes.get(rut);
            if (p == null) {
                p = new IspPersonaCodigo(rut, cargo, prac);
            } else {
                p.setCodCargo(cargo);
                p.setCodPrac(prac);
            }
            guardar.add(p);
        }
        personaRepo.saveAll(guardar);
        return guardar.size();
    }

    private int upsertClientes(String empresa, Map<String, Map<String, Integer>> tecCnt) {
        List<IspClienteTecnologia> guardar = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : tecCnt.entrySet()) {
            String rutEnt = e.getKey();
            String tec = modaStr(e.getValue());
            IspClienteTecnologia t = clienteTecRepo
                    .findByEmpresaAndRutEntidad(empresa, rutEnt)
                    .orElseGet(() -> new IspClienteTecnologia(empresa, rutEnt, tec));
            t.setTecnologia(tec);
            guardar.add(t);
        }
        clienteTecRepo.saveAll(guardar);
        return guardar.size();
    }

    private String cell(DataFormatter fmt, Row row, int idx) {
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private Integer intCell(DataFormatter fmt, Row row, int idx) {
        String s = cell(fmt, row, idx).replaceAll("[^0-9-]", "");
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void contar(Map<String, Map<Integer, Integer>> map, String key, Integer val) {
        map.computeIfAbsent(key, k -> new HashMap<>()).merge(val, 1, Integer::sum);
    }

    private static void contarStr(Map<String, Map<String, Integer>> map, String key, String val) {
        map.computeIfAbsent(key, k -> new HashMap<>()).merge(val, 1, Integer::sum);
    }

    private static Integer moda(Map<Integer, Integer> counter) {
        if (counter == null || counter.isEmpty()) return null;
        return counter.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    private static String modaStr(Map<String, Integer> counter) {
        if (counter == null || counter.isEmpty()) return null;
        return counter.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }
}
