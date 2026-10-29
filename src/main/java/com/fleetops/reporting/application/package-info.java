/**
 * Capa de lógica de negocio.
 * <p>
 * Va aquí: el servicio orquestador (patrón Aggregator / Scatter-Gather) que consulta las fuentes en paralelo,
 * el cálculo del período (rango) y el ensamblado de la respuesta con fuentes_no_disponibles.
 * Depende de interfaces de {@code integration}, nunca de clientes HTTP o de SQS concretos.
 */
package com.fleetops.reporting.application;
