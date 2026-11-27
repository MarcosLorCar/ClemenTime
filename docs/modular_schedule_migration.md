# Migración del pipeline: índice temporal

Este archivo es un punto de entrada temporal, no una segunda lista de tareas.
El diseño está en [Pipeline modular de horarios](modular_schedule_pipeline.md).
**El estado, alcance detallado y aceptación de cada entrega viven en GitHub.**

## Secuencia de PRs

1. [#47 — Contrato y caracterización ESI](https://github.com/MarcosLorCar/ClemenTime/issues/47).
2. [#48 — Núcleo modular y adaptador ESI](https://github.com/MarcosLorCar/ClemenTime/issues/48).
3. [#49 — Catálogo, publicación y CI por facultad](https://github.com/MarcosLorCar/ClemenTime/issues/49).
4. [#50 — Adaptador ESII y cierre documental](https://github.com/MarcosLorCar/ClemenTime/issues/50).

Cada issue depende del anterior y está pensado para una PR revisable. La PR que
añade este plan no implementa ni cierra ninguno. Las entregas se ejecutan una a
una, comprobando sus criterios antes de iniciar la siguiente. Si una entrega
necesita dividirse, se actualiza primero su issue con las nuevas dependencias.

## Caducidad explícita

La PR que cierre #50 debe eliminar este archivo y los enlaces temporales que
apunten a él, y convertir el diseño en documentación del comportamiento real.
Esa limpieza es un criterio de aceptación del issue, no una tarea para una futura
auditoría. Si ESII se pospone o cancela, se retira igualmente este índice al cerrar
la migración del núcleo y se conserva su trabajo pendiente en un issue separado.

No se duplican aquí checklists, fechas objetivo ni estados de los issues.
