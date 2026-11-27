# Pipeline modular de horarios

Estado: **contrato documentado y salida ESI caracterizada con pruebas offline**.
El pipeline operativo sigue usando los scripts ESI. El registro de adaptadores,
el núcleo de publicación y la integración ESII siguen siendo propuestas.

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

## Consumidores actuales

| Consumidor | Entrada y comportamiento actual |
| --- | --- |
| CLI `process_schedules.py` | Acepta un JSON local opcional, `--strict` y `--check-esi`. `--force` se pasa al comprobador si se activa la comprobación. Ejecuta el parser y después el generador de índices. Cuando el comprobador termina correctamente, el CLI procesa el fichero local aunque las cabeceras no hayan cambiado. |
| `sync-esi-schedules.yml` | Consume `has_update` del comprobador HTTP. Ejecuta el CLI estricto si hay actualización o si arranca por push o manualmente. Confirma y sube los cambios en `schedules/`; notifica tras el push o en modo de prueba manual, con `success()`. |
| `update-schedules-index.yml` | Ante una edición de `dist/` en `master`, excluidos los dos índices, ejecuta directamente `generate_index.py`. Publica los índices si difieren y notifica tras el push. Ambos workflows comparten el grupo de concurrencia `schedules-master-write`. |
| Catálogo v2 | `RemoteScheduleFetcher` lee `schedules_catalog.json`. La jerarquía es `universities → faculties → terms`; cada periodo contiene `id`, `name`, `description`, `path`, `hash` y `updatedTime`. Android resuelve `path` desde la base del catálogo. ESI publica las rutas relativas `1C.json` y `2C.json`. |
| Índice legacy | `schedules_index.json` es un array plano ESI con los mismos datos de periodo y `title` en lugar de `name`. Android puede envolverlo en un catálogo sintético UCLM/ESI como fallback. El generador toma de este índice la fecha anterior de cada periodo. |
| Parser Android | `JsonScheduleParser` lee los arrays de `JsonFlatSlot` y los agrupa por curso, grupo y asignatura; las sesiones `evento` se tratan como comunes. El parser también admite el JSON anidado para importaciones. |
| Payload FCM | `notify_fcm.py` lee el catálogo y construye un mensaje por facultad con `university`, `faculty`, `updated_at` y `hash_{termId}` como valores de datos de tipo string. ESI conserva el topic `sched_uclm_esi` y las claves `hash_1C` y `hash_2C`. Android usa `TermCodeParser` con el sufijo de la clave para seleccionar el hash del periodo. |

## Comportamiento ESI caracterizado

Las [muestras pequeñas](../schedules/script/fixtures/esi) y las [pruebas del
pipeline](../schedules/script/test_schedule_pipeline.py) fijan el JSON plano
publicado por ESI, incluidos `codigo` (identificador corto) y `asignatura`
(nombre visible), eventos `GENERAL`, sesiones compartidas, deduplicación,
sufijos de laboratorio, redondeo de fin a `:50` y filtrado de Universidad de
Mayores. Los dos índices reflejan el SHA-256 de los bytes de cada fichero y
mantienen `updatedTime` por periodo mientras su hash coincida con el índice legacy
anterior. Las [pruebas HTTP](../schedules/script/test_check_esi_update.py) simulan
HEAD y GET, sin consultar el servidor.

La implementación actual tiene estos límites:

- El modo estricto aborta si quedan asignaturas o profesores sin mapping. El
  parser publica el aula saneada sin consultar `mappings.classrooms`. El aborto
  preserva ambos horarios, ambos índices y los mappings. Si se pidió comprobar
  ESI primero, el comprobador ya pudo actualizar el JSON de entrada y el estado
  HTTP antes de que falle el parser.
- Una respuesta con cabeceras nuevas y los mismos bytes actualiza el estado
  HTTP, reescribe el fichero de entrada y emite `has_update=false`. El comprobador
  admite un objeto JSON no vacío, aunque el parser requiere una lista. La
  diferencia entre un fallo HTTP y una fuente sin cambios se pierde en
  `has_update=false` si se usa el modo tolerante por defecto.
- El parser solo escribe los periodos con sesiones y deja en disco el fichero
  anterior si falta un periodo en la fuente. Escribe directamente en el destino;
  la comprobación previa de mappings protege de ese fallo concreto.
- El orden ESI es estable para la misma entrada. Los empates en grupo, día,
  inicio y código conservan el orden de origen. La escritura en modo texto usa
  los saltos de línea del sistema, por lo que un mismo horario puede tener hashes
  distintos en Windows (CRLF) y en el entorno de publicación Linux (LF).
- El aviso de sincronización responde a cualquier commit bajo `schedules/`,
  incluso si solo cambió el estado HTTP en una ejecución que llegó al paso de
  publicación. El emisor recorre todas las facultades del catálogo recibido.

## Separación de responsabilidades propuesta

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

El adaptador adquiere la fuente y normaliza sus sesiones. El núcleo recibe mapas
por periodo, contrasta los periodos recibidos con la configuración y serializa
los resultados válidos. Estas firmas definen los datos internos para
la extracción posterior.

```text
FacultyKey(university_id: str, faculty_id: str)
FacultySpec(key: FacultyKey, university_name: str, faculty_name: str,
            periods: tuple[PeriodSpec, ...], fcm_topic_prefix: str | None,
            subgroups_label: str | None, period_type: str | None, period_count: int | None)
PeriodSpec(id: str, index: int, name: str, description: str | None, path: str)
Session(subject_code: str, subject_name: str, group: str, day: int,
        start: time, end: time, kind: str, room: str, professor: str,
        is_lab: bool, subgroup: str)
FacultySchedule(key: FacultyKey, sessions_by_period: Mapping[str, tuple[Session, ...]])

acquire() -> Unchanged | Candidate[source_payload] | SourceFailure
normalize(source_payload) -> FacultySchedule | ParseFailure
```

- El registro usa una clave estable y no vacía por par universidad/facultad.
  Cada facultad declara IDs e índices de periodo únicos. El núcleo valida que los
  IDs de `sessions_by_period` correspondan a los periodos declarados, que las
  rutas relativas permanezcan en `dist/` y que cada ruta publicada sea única.
- El índice es un entero de 1 a 8. El ID publicado debe resolverse al mismo
  índice en `TermCodeParser` tanto desde el catálogo como desde `cuatrimestre` y
  `hash_{termId}`. Para ESI se mantienen `1C` y `2C`. La existencia de alias o
  fallbacks en Android no acredita compatibilidad con IDs arbitrarios.
  Si se declaran `period_type` o `period_count`, la configuración que Android
  obtiene mediante `PeriodConfig.inferFromCatalog` debe abarcar todos los
  índices publicados. El catálogo ESI actual omite ambos campos.
- `Session` conserva por separado código y nombre, y un registro por grupo
  asistente. `day` usa lunes = 1 a domingo = 7; `start` y `end` son horas
  locales con precisión de minutos. Se conservan `kind`, `is_lab` y `subgroup`
  por separado para representar la semántica de la fuente. Un evento común ESI
  se representa con `group = "GENERAL"` y `kind = "evento"`.
- El serializador convierte cada sesión en el objeto plano actual. Toma
  `cuatrimestre` de la clave del periodo y escribe `grupo`, `dia` (nombre en
  español), `hora_inicio` y `hora_fin` (HH:mm), `codigo`, `asignatura`,
  `tipo`, `aula`, `profesor`, `es_laboratorio` y `grupo_practicas`.
  Universidad, facultad y metadatos del periodo se publican en los índices
  existentes. El hash se calcula sobre los bytes finales.
- `Candidate` contiene datos por validar y puede proceder de un reprocesado
  local. `SourceFailure` y `ParseFailure` conservan el diagnóstico e impiden que
  se publique ese resultado. `Unchanged` conserva la última publicación. Un
  periodo vacío solo es válido si la configuración y las comprobaciones de
  integridad de esa fuente lo permiten de forma explícita.

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
