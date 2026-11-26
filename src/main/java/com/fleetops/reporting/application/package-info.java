/**
 * Capa de lógica de negocio.
 * <p>
 * Aquí está el orquestador {@code ServicioReporte} (patrón Aggregator), el cálculo del período en hora de Colombia
 * ({@code CalculadorPeriodo}), la caché de reportes completos y la respuesta del contrato
 * ({@code ReporteDisponibilidad}). El subpaquete {@code fuentes} hace el Scatter-Gather y define los puertos
 * ({@code PuertoFuentesHttp}, {@code PuertoBaseLectura}) que implementa {@code integration}: esta capa nunca
 * depende de un cliente HTTP, de SQS ni de JPA concretos.
 */
package com.fleetops.reporting.application;
