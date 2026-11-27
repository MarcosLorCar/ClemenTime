# Pipeline modular de horarios

Estado: **diseño propuesto; todavía no implementado**.

Este documento define los límites de la migración. El comportamiento operativo
actual se describe en [la guía ESI](esi_api_schedule_pipeline.md). Las tareas,
dependencias, criterios de aceptación y progreso se mantienen exclusivamente en
los issues [#47](https://github.com/MarcosLorCar/ClemenTime/issues/47),
[#48](https://github.com/MarcosLorCar/ClemenTime/issues/48),
[#49](https://github.com/MarcosLorCar/ClemenTime/issues/49) y
[#50](https://github.com/MarcosLorCar/ClemenTime/issues/50).

## Objetivo y alcance

Añadir facultades mediante adaptadores independientes que entregan un contrato
común. ESI (Ciudad Real) conserva su API; ESII (Albacete), también de UCLM,
tendrá un adaptador HTML. La facultad es la unidad de configuración y publicación;
su adaptador puede consultar varias páginas o fuentes.

No se implementará un scraper universal basado únicamente en cambiar URLs.
Tampoco se introduce un servidor, plugins externos, un nuevo formato público ni
una migración Android como parte de esta propuesta. Si la fuente ESII requiere
capacidades adicionales del cliente, se abrirá una decisión de alcance separada.

## Punto de partida

- `process_schedules.py` coordina directamente los scripts ESI.
- `parse_schedule.py` mezcla interpretación de la fuente, mappings, reglas ESI y
  escritura de los archivos `1C.json` y `2C.json`.
- `generate_index.py` genera un catálogo v2 con UCLM/ESI fijados en el código.
- Los workflows de sincronización y edición manual comparten la exclusión mutua
  de escritores, pero ambos deben adaptarse al catálogo de varias facultades.
- `notify_fcm.py` recorre todas las facultades del catálogo recibido; la selección
  de facultades realmente modificadas debe incorporarse a la publicación.

Estas observaciones justifican la migración; no describen capacidades ya añadidas.

## Separación de responsabilidades

### Registro y adaptadores

Un registro explícito asocia `(university_id, faculty_id)` con el adaptador y sus
metadatos: nombres, periodos, rutas de salida y configuración de la fuente.
`uclm/esi` y `uclm/esii` son registros diferentes. No hay una facultad por defecto
en el núcleo. ESI conserva sus identificadores y topic existentes.

Cada adaptador controla adquisición, detección de cambios e interpretación de su
fuente. Sus mappings, fixtures y estado HTTP quedan aislados. Las reglas ESI
(:50, sufijos de laboratorio, eventos GENERAL, filtros y nombres) permanecen en
ESI. Las funciones verdaderamente compartidas se extraen con pruebas, sin copiar
reglas a cada adaptador ni convertir peculiaridades ESI en reglas globales.

No se exige HEAD/ETag a todas las fuentes. El adaptador HTML podrá usar descargas
condicionales o hashes según lo que soporte la fuente. Los cambios de código,
configuración y mappings también deben permitir regenerar resultados aunque la
fuente remota no haya cambiado. Debe existir reprocesado offline de fixtures.

### Contrato y núcleo

La frontera lógica es: fuente -> adaptador -> sesiones normalizadas por periodo
-> validación -> serialización -> catálogo y publicación.

El resultado identifica universidad, facultad y periodos, con sesiones que
conservan código y nombre de asignatura, grupo, día, horas, aula, profesor y
semántica de laboratorio/subgrupo/evento del formato actual. Las firmas y tipos
concretos se fijan en #47. No se añade un envoltorio nuevo al JSON público.

El núcleo valida identidad, referencias, campos, horas, periodos y rutas; produce
salida determinista y hashes de los bytes finales. No impone dos cuatrimestres,
pero solo se publicarán periodos compatibles con el cliente: actualmente su
modelo admite índices 1..8. No se asume soporte para IDs arbitrarios.

La adquisición distingue datos sin cambios, datos candidatos y error. El resultado
por facultad distingue actualización válida, ausencia de cambios y fallo. Un fallo
de parsing nunca equivale a un horario vacío válido. Las políticas de integridad
de cada fuente deben detectar extracción parcial y desaparición de periodos.

### Publicación y notificaciones

Cada facultad prepara y valida su resultado en staging. Un único publicador
ensambla los resultados válidos y conserva íntegra la última publicación de las
facultades fallidas. Una facultad nueva que falla no aparece vacía en el catálogo.
Las eliminaciones de periodos deben ser explícitas, no inferidas de un fallo.

El catálogo y los horarios se publican como un lote coherente en Git. Esto no
garantiza lecturas atómicas entre URLs HTTPS: #49 debe verificar la comprobación
de hashes y la recuperación del cliente ante versiones temporalmente desalineadas.
Si requiere cambios Android, deberán concretarse antes de activar otra facultad.

Solo después del push confirmado se envía FCM a las facultades cuyos horarios
publicados cambiaron. Cambios únicamente en metadatos de descarga no notifican.
Un fallo FCM no revierte una publicación: debe quedar visible y ser reintentable.
El modo manual de notificación de prueba permanece explícito.

Los dos workflows usarán el mismo catálogo y reglas de publicación, conservarán
la exclusión mutua de escritores y aplicarán `success()` donde corresponda.
El estado de una ejecución parcial debe mostrar qué facultad falló aunque se
hayan publicado otras correctamente.

## Compatibilidad que se debe preservar

- ESI sigue publicando `schedules/dist/1C.json` y `2C.json`. Las nuevas facultades
  usan rutas propias, por ejemplo `schedules/dist/uclm/esii/1C.json` si ese periodo
  existe. No se cambian rutas ESI para conseguir simetría de carpetas.
- El catálogo sigue siendo v2 con `universities -> faculties -> terms`, usando
  los campos reales del contrato, incluidos `path`, `hash` y `updatedTime`.
- `schedules_index.json` sigue siendo la vista legacy exclusiva de ESI.
- Los archivos de sesiones siguen siendo arrays planos; `codigo` y `asignatura`
  conservan sus significados. Los hashes y fechas permanecen estables si no hay
  cambios de contenido. La comparación inicial debe detectar también diferencias
  de serialización que generarían avisos innecesarios.
- Se conservan `sched_uclm_esi` y los campos FCM `hash_{termId}`. No se introducen
  defaults ESI/UCLM para nuevas instalaciones Android.
- Los consumidores del CLI y scripts se actualizan con su sustitución. Se elimina
  el código supersedido en la misma PR, sin mantener dos implementaciones.

## Pendientes de la fuente ESII

El usuario indica que la fuente es HTML y que tiene permiso de extracción. Falta
identificar la URL concreta e inspeccionar muestras: número de páginas, estructura
de tablas, periodos, grupos y posible separación de titulaciones. No se presupone
JavaScript, autenticación, una biblioteca de scraping ni reglas de horarios.
Estos puntos se resuelven en #50 antes de activar ESII en producción.

## Mantenimiento de este documento

Cada PR de implementación actualiza las secciones afectadas y la documentación
operativa/skill correspondiente. Mientras la migración sea parcial, debe indicar
qué partes están implementadas; no basta con cambiar el estado global.

Al cerrar #50, este archivo debe describir únicamente la arquitectura entregada,
con enlaces a los módulos y comandos reales. Se retiran el punto de partida,
los pendientes resueltos y los textos de propuesta. Las decisiones históricas y
tareas pendientes quedan en issues y en Git. Si ESII se pospone o cancela, se hace
igualmente este cierre documental para el núcleo entregado. No se deja su limpieza
para una auditoría futura.
