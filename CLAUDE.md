# Instrucciones para Claude

Clon de Minecraft en Java 21 con LWJGL 3 y Maven. Todo el contexto necesario está en el repo, no en conversaciones anteriores. Responder en español.

## Antes de empezar

1. Leer el plan actual, `docs/PLAN_OPTIMIZACION.md`: la tabla "Estado de las fases" dice qué fase sigue. El menú de inicio (`docs/PLAN_MENU_INICIO.md`) ya está terminado y probado.
2. Leer `docs/ARQUITECTURA.md` para entender cómo está organizado el código.

## Una fase por conversación

- Trabajar solo la fase que sigue; no adelantar fases.
- Al terminar la fase:
  1. `mvn clean compile` sin errores.
  2. Actualizar su fila en la tabla del plan actual (estado, commit, mediciones y qué falta probar en Windows).
  3. Commit y push a la rama de trabajo.
  4. Cerrar con un resumen corto, decir qué probar en el juego y recordarle al usuario que haga `/clear` antes de pedir la siguiente fase.
- El `/clear` entre fases es para ahorrar créditos: cada mensaje reenvía toda la conversación, así que empezar cada fase limpia sale más barato.

## Rama

- La reestructuración y los docs están en `claude/folder-redistribution-feedback-axvqj2` y todavía no en `main`. Mientras no se fusionen, trabajar sobre esa rama (o una basada en ella), no sobre `main`.
- No hacer push a `main` a menos que el usuario lo pida.

## Pruebas

- Aquí no se puede abrir el juego (no hay pantalla ni GPU). Verificar con `mvn clean compile` / `mvn package`; la prueba visual la hace el usuario en Windows, ejecutando `com.minejava.Main.Launcher`.
- En la optimización, medir aquí con la herramienta sin pantalla de la fase 1 y pedirle al usuario que pegue la salida de la consola del juego en Windows.
