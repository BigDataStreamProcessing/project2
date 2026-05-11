package com.example.bigdata.prosumenci.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Zdarzenie strumieniowe: pojedynczy odczyt licznika prosumenta.
 *
 * Agregat L1: bilans energetyczny per prosument w oknie 15-minutowym
 *   – suma produkcji, suma konsumpcji, netto (eksport/import), koszt
 * Agregat L2: bilans osiedla/gminy w oknie godzinnym
 *   – sumaryczna moc, procent autokonsumpcji, wartość wymiany z siecią
 * Anomalia: produkcja > moc_instalacji * 1.15 (usterka licznika / inverter),
 *           konsumpcja > 10 kWh / 15 min (niezidentyfikowany odbiornik)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProsumentEvent {

    /** Unikalny identyfikator prosumenta (np. UUID lub nr. PPE) */
    private String prosumentId;

    /** Kod taryfy – klucz do słownika taryf */
    private String tariffCode;

    /** Chwila odczytu (UTC) */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    /** Produkcja PV w kWh w ostatnim interwale */
    private double productionKwh;

    /** Konsumpcja z sieci + lokalnej produkcji w kWh */
    private double consumptionKwh;

    /** Moc chwilowa produkcji w kW */
    private double pvPowerKw;

    /** Moc chwilowa poboru w kW */
    private double loadPowerKw;

    /** Energia oddana do sieci w kWh (eksport) */
    private double gridExportKwh;

    /** Energia pobrana z sieci w kWh (import) */
    private double gridImportKwh;

    /** Stan baterii domowej [0..100 %] lub -1 jeśli brak */
    private double batteryStatePercent;

    /** Czy trwa ładowanie pojazdu elektrycznego */
    private boolean evCharging;

    /** Napięcie fazowe [V] */
    private double voltageV;

    /** Temperatura modułów PV [°C] */
    private double pvTemperatureC;

    /** Nasłonecznienie [W/m²] */
    private double irradianceWm2;

    /** Gmina / osiedle – klucz do agregatu L2 */
    private String locality;
}