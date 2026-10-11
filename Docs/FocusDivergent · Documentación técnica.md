# FocusDivergent · Documentación técnica

Oct 11, 2026 · @Hector

## Resumen

FocusDivergent es una app nativa de Android, escrita en Kotlin y Jetpack Compose. Protege la atención frente a las apps que la roban y ayuda a empezar tareas. La versión actual es la 1.0.0 beta, y todo funciona sin conexión a internet.

### Para quién es

«Neurodivergente» describe a personas cuyo cerebro funciona de forma distinta a la mayoría: TDAH, autismo, dislexia, dispraxia, síndrome de Tourette, altas capacidades y otros perfiles. No es un diagnóstico único, y cada persona combina rasgos distintos.

La app se centró en el TDAH y las altas capacidades, pero la dificultad que aborda es común a muchos perfiles: la función ejecutiva. Cuesta empezar, calcular el tiempo, frenar un impulso y volver tras una interrupción. El diseño responde a esa dificultad y no exige un diagnóstico.

Cada perfil tiene necesidades propias, y la app no las cubre por igual. Una persona autista con hipersensibilidad se beneficia de un diseño sin destellos y con movimiento reducido. La captura de ideas o los micro-pasos ayudan con la parálisis, pero no sustituyen a apoyos específicos. La app no reemplaza a un profesional.

El repositorio se organiza así:

| Carpeta | Contenido |
| --- | --- |
| `app/src/main/java/com/focusdivergent/app/domain` | Modelos, políticas y casos de uso. Sin dependencias de Android. |
| `app/src/main/java/com/focusdivergent/app/data` | Room, preferencias, uso de apps e implementación de los repositorios. |
| `app/src/main/java/com/focusdivergent/app/presentation` | Pantallas Compose, ViewModels y navegación. |
| `app/src/main/java/com/focusdivergent/app/service` | Accesibilidad, servicio de bloqueo, alarmas, notificaciones y audio. |
| `app/src/main/java/com/focusdivergent/app/di` | Módulos de Hilt que enlazan interfaces e implementaciones. |
| `app/src/main/res` | Textos en `values/strings.xml`, iconos y sprites de las mascotas. |
| `app/src/test` | Tests unitarios y de pantalla sobre Robolectric. |
| `app/schemas` | Esquema de Room exportado en JSON, uno por versión de la base. |
| `fastlane/metadata` | Ficha de F-Droid y notas de versión. |

## Arquitectura

&#91;embedded content: Capas de FocusDivergent · las flechas van hacia el dominio\]

La app se divide en cuatro capas. Cada flecha indica una dependencia: quien apunta, depende del otro. El dominio no conoce Android, así que sus reglas se prueban en la JVM sin emulador.

## Capas y paquetes

Cada capa tiene una responsabilidad y una regla. Las reglas se comprobaron en el código actual.

| Paquete | Qué contiene | Regla |
| --- | --- | --- |
| `domain` | Modelos, políticas puras, casos de uso e interfaces de repositorio. | Ningún import de `android.*` ni `androidx.*`. |
| `data` | Room (DAOs, entidades, migraciones), repositorios, preferencias y uso de apps. | No importa `presentation`. |
| `presentation` | Pantallas Compose, ViewModels, navegación y tema. | No importa `data`: pide todo a través del dominio. |
| `service` | Servicio de accesibilidad, servicio de bloqueo, alarmas, notificaciones, receptores y audio. | Llama a casos de uso. Excepción: el overlay importa pantallas de `presentation`. |
| `di` | Módulos de Hilt que enlazan cada interfaz con su implementación. | Solo cableado, sin lógica. |

La excepción de `service` está en `CaptureTileService`, `FocusBlockerService` e `InterruptionOverlayController`. Dibujan pantallas de Compose fuera de la actividad, así que necesitan sus composables. Un cambio nuevo no debe seguir este camino sin justificarlo.

## Motor de bloqueo

El bloqueo es una cadena corta. Android avisa del cambio de app, un servicio decide y una ventana superpuesta muestra la pausa. Ninguna pieza lee el contenido de la pantalla.

1. `AppBlockerAccessibilityService` recibe cada cambio de ventana y llama a `BlockCheckTrigger.request()`. No decide nada.
2. `FocusBlockerService` es un servicio en primer plano y `START_STICKY`. Junta esos avisos con un tic cada 5 segundos, y deja al menos 300 ms entre comprobaciones.
3. Cada comprobación aplica antes los cambios de límite que ya vencieron, cierra el día si toca y evalúa si hay una interrupción.
4. `EvaluateInterruptionUseCase` no interrumpe con la pantalla apagada, con una llamada en curso o en una app de marcación. Si no, lee el uso de hoy y busca en este orden: aviso de Obligación, Lanzadera, bloqueo de app y Reality Check.
5. `InterruptionOverlayController` muestra o retira la ventana. Cada interrupción tiene una clave, y mientras no cambie el overlay no se vuelve a dibujar.

| Interrupción | Cuándo aparece | Cómo se resuelve |
| --- | --- | --- |
| Aviso de Obligación | 10 minutos antes de su hora (`ReminderPolicy.LEAD_MINUTES`). | Apagando la pantalla, al llegar la hora o resolviendo el reto. |
| Lanzadera | Durante 2 minutos, tras elegir empezar. | «Seguir» deja la tarea en marcha; «Lo dejo por ahora» no deja huella. |
| Bloqueo de app | Al agotar el límite diario, según el nivel de fricción. | Flexible: reto para ganar 15 minutos, hasta 3 veces. Firme: cierra hasta mañana. Cerrada: siempre en pausa. |
| Reality Check | Tras un rato seguido en una app. | «Sí, es a propósito» silencia el aviso un tiempo; o se sale al inicio. |

## Modelo de datos

La base es Room, en la versión 8. Es la única fuente de verdad, y nada sale del dispositivo. Cada versión exporta su esquema en `app/schemas/`.

| Tabla | Contenido | Notas |
| --- | --- | --- |
| `tasks` | Tareas, Obligaciones y Rutinas. | Fecha de inicio en epoch ms. Repetición en RRULE. |
| `task_steps` | Micro-pasos de una tarea. | Se borran en cascada con su tarea. |
| `step_templates` | Plantillas de pasos del usuario. | Los pasos van separados por saltos de línea. |
| `app_limits` | Límites diarios por app. | Incluye nivel de fricción y marca de «ocio». |
| `pending_limit_changes` | Relajaciones de límite a la espera de medianoche. | Se aplican solas al vencer. |
| `time_extensions` | Ampliaciones de 15 minutos ganadas con el reto. | Una fila por ampliación. |
| `routine_completions` | Rutinas hechas, por día. | Una fila por rutina y día. |
| `xp_events` | Libro de XP y monedas. | Un movimiento por causa, actividad y día. El total se suma. |
| `cosmetic_purchases` | Cosméticos comprados. | Comprar dos veces no cobra dos veces. |
| `user_stats` | Una sola fila: la mejor racha. | No guarda contadores de XP ni de monedas. |
| `brain_dumps` | Ideas de la bandeja. |  |
| `vault_nodes` | Notas y carpetas de la Bóveda. | Jerarquía por `parent_id`. |
| `vault_nodes_fts` | Búsqueda de texto completo de la Bóveda. | Tabla FTS4 sin acentos. |

El historial de versiones está en `FocusDatabase.kt`, y estas son sus migraciones:

| Versión | Cambio |
| --- | --- |
| 1 | Esquema inicial. |
| 2 | Tabla `time_extensions`. |
| 3 | `tasks.estimated_minutes`, `tasks.completed_at` y `routine_completions`. |
| 4 | `app_limits.extensions_per_day` y `pending_limit_changes`. |
| 5 | `vault_nodes.updated_at`. |
| 6 | Libro de XP, cosméticos y `user_stats.best_streak`. Migración manual (`Migration5To6`) que anota en el libro lo ya hecho. |
| 7 | `app_limits.is_leisure`, `pending_limit_changes.is_leisure` y `xp_events.minutes`. |
| 8 | `task_steps` y `step_templates`. |

La configuración del Cierre de Jornada y el último día cerrado viven en preferencias. No necesitan relaciones.

## Recompensas, racha y mascota

El XP y las monedas nunca se guardan como contador. Cada cosa hecha es un movimiento en `xp_events`, y el total se suma al mostrarlo. Así, marcar y desmarcar no puede inflar nada.

Los valores están en `RewardRules`, en un solo fichero:

| Causa | XP | Monedas |
| --- | --- | --- |
| Tarea u Obligación terminada | 10 (15 si pide energía alta) | 1 |
| Rutina hecha en el día | 8 | 1 |
| Bloque de enfoque de 10 minutos o más | 5 por cada tramo de 15 minutos | 1 por bloque |
| Seguir tras la Lanzadera | 5 | 0 |
| Paso de una tarea hecho | 2 | 0 |
| Límite respetado al Cierre de Jornada | 5 por app | 0 |

Hay topes diarios de 100 XP y 10 monedas. Desmarcar una actividad borra su movimiento. Borrar la actividad no quita nada.

**Racha.** `StreakCalculator` cuenta los días con al menos una rutina hecha. Un día vacío por semana se perdona sin avisar. Hoy nunca rompe la racha, porque el día aún no terminó. Si se rompe, no hay mensaje, y la mejor racha se conserva.

**Niveles.** La curva es `30 + 10 × nivel` de XP por nivel, sin techo. `Levels.of` calcula el nivel a partir del XP total.

**Mascota.** Hay tres compañeros en pixel art: Zorro, Gato y Alien. Cada uno tiene cinco etapas, que empiezan en los niveles 1, 3, 6, 10 y 15 (`Mascot.LEVELS`). Los cosméticos se compran con monedas y se colocan junto a la mascota.

## Pantallas y navegación

La navegación es Navigation Compose con rutas tipadas: cada destino es una clase `@Serializable` en `Routes.kt`. Un destino mal escrito o un argumento que falta es un error de compilación. Hay cuatro pestañas (`TopLevelDestination`): Hoy, Tareas, Bóveda y Bloqueos. Cambiar de pestaña no apila pantallas, y cada una conserva su estado.

| Ruta | Pantalla | ViewModel |
| --- | --- | --- |
| `TodayRoute` | Hoy: energía, Elige por mí, Lanzadera, ahora y mascota | `TodayViewModel` |
| `ChooseForMeRoute` | Elige por mí | `ChooseForMeViewModel` |
| `FocusLaunchRoute` | Lanzadera de 2 minutos | `FocusLaunchViewModel` |
| `TasksRoute` | Lista de tareas, Obligaciones y rutinas | `TasksViewModel` |
| `TaskEditorRoute` | Crear o editar una actividad | `TaskEditorViewModel` |
| `StepsRoute` | Preparar los pasos de una tarea | `StepsEditorViewModel` |
| `StepFocusRoute` | Un paso por pantalla | `StepFocusViewModel` |
| `TimerRoute` | Temporizador y Pomodoro | `TimerViewModel` |
| `AudioRoute` | Sonido ambiente | `AudioViewModel` |
| `MascotRoute` | Mi mascota y la tienda | `MascotViewModel` |
| `VaultRoute` | Bóveda y bandeja de ideas | `VaultViewModel` |
| `NoteEditorRoute` | Crear o editar una nota | `NoteEditorViewModel` |
| `BlocksRoute` | Bloqueos: límites y accesos | `BlocksViewModel` |
| `AppPickerRoute` | Elegir la app de un límite | `AppPickerViewModel` |
| `LimitEditorRoute` | Crear o cambiar un límite | `LimitEditorViewModel` |
| `ClosureSettingsRoute` | Ajustes del Cierre de Jornada | `ClosureSettingsViewModel` |
| `ClosureRoute` | Resumen del día cerrado | `ClosureSummaryViewModel` |
| `AiSettingsRoute` | Pasos con tu propia IA | `AiSettingsViewModel` |
| `PermissionsRoute` | Permisos y batería; primera vez, la bienvenida | `PermissionsViewModel` |

La captura rápida de ideas tiene su propia actividad, `CaptureActivity`, con `CaptureViewModel`. Se abre desde la notificación o desde el mosaico de Ajustes rápidos, encima de cualquier app. La pantalla de bienvenida se muestra una sola vez.

## Servicios, alarmas y avisos

Cada tipo de aviso tiene su receptor, su código de alarma y su canal. Dos alarmas con el mismo código y el mismo `Intent` se sustituyen entre sí. Por eso el «Más tarde» de cada rutina lleva su propio dato en el `Intent`.

| Componente | Qué hace | Código o canal |
| --- | --- | --- |
| `FocusBlockerService` | Vigila las apps y muestra las interrupciones. Servicio en primer plano. | Canal `focus_protection` |
| `AppBlockerAccessibilityService` | Avisa de cada cambio de ventana. | Permiso de accesibilidad |
| `ObligationAlarmReceiver` | Aviso de una Obligación, 10 minutos antes. | Alarma 1 |
| `TimerAlarmReceiver` | Fin de cada fase del temporizador. | Alarma 2 · canales `focus_timer` y `focus_timer_alerts` |
| `ClosureAlarmReceiver` | Hora del Cierre de Jornada: cierra el día y programa el siguiente. | Alarma 4 · canal `day_closure`, notificación 30 |
| `RoutineAlarmReceiver` | Aviso general de rutinas con hora. | Alarma 6 · canal `routine_reminders` |
| `RoutineSnoozeReceiver` | Repite una vez el aviso de una rutina pospuesta. | Alarma 7 |
| `RoutineActionReceiver` | Los botones «Hecha» y «Más tarde» del aviso. | Acciones de notificación |
| `AmbientAudioService` | Reproduce el sonido ambiente con la app cerrada. | Canal `ambient_audio` |
| `CaptureTileService` | Mosaico «Anotar idea» de Ajustes rápidos. | Servicio de mosaico |
| `*RestartReceiver` (temporizador, cierre, rutinas y protección) | Vuelve a poner las alarmas tras reiniciar o actualizar, porque Android las borra. | `BOOT_COMPLETED` y `MY_PACKAGE_REPLACED` |

Al arrancar, `FocusDivergentApp` vuelve a programar el Cierre de Jornada, y los sincronizadores de Obligaciones y de rutinas observan la base de datos. Cada vez que cambia una tarea o una rutina, recalculan la siguiente alarma.

Las alarmas son exactas si el usuario concedió el permiso de alarmas exactas. Si no, son aproximadas, y el código lo asume: el Cierre se hace también al abrir la app o desde el servicio de bloqueo.

## Privacidad y garantías

Las dos promesas centrales de la app, sin conexión y sin castigo visual, no dependen de la buena voluntad de quien programe. Hay tests que fallan si se rompen.

| Garantía | Qué la hace cumplir | Test |
| --- | --- | --- |
| Sin red, salvo la IA opcional | Solo `ChatCompletionsSuggester` y `UrlConnectionPoster` abren conexiones. Solo `https`. Las redirecciones no se siguen. | `InternetGuardTest` |
| Ningún permiso de red extra | Solo `INTERNET`, sin `ACCESS_NETWORK_STATE` ni tráfico en claro. | `InternetGuardTest` |
| Cero rojos | Busca colores rojizos en `src/main` y el uso de `Color.Red` o `Color.Magenta`. | `Rnf3GuardTest` |
| Textos sin reproche ni alarma | Busca palabras como «átrasada» o «culpa», exclamaciones y palabras en mayúsculas dentro de `strings.xml`. | `Rnf3GuardTest` |
| Contraste alto | El texto sobre cada fondo llega a 7:1, en las dos paletas. | `ThemeContrastTest` |
| Política y manifiesto coinciden | Cada permiso aparece en `PRIVACY.md`. El servicio de accesibilidad no puede leer la pantalla. Las copias de seguridad están apagadas. | `ReleaseReadinessTest` |

La clave de la IA se cifra con AES-GCM en el Keystore de Android, y no se guarda en preferencias en claro (`KeystoreSecretCipher`). Ninguna copia de seguridad la incluye, porque `allowBackup` está apagado.

Si un cambio rompe una garantía, el test falla en el build. El cambio no se corrige cambiando el test: hay que replantear el cambio o, si la decisión es deliberada, documentarla en `PRIVACY.md` y en este apartado.

## Pruebas

Hay 1.140 tests unitarios en 92 ficheros de test, todos en `app/src/test`. No hay tests instrumentados que necesiten un dispositivo. Los de pantalla y base de datos se ejecutan sobre Robolectric, en la JVM.

| Qué se prueba | Cómo | Dónde |
| --- | --- | --- |
| Reglas de negocio | Funciones puras y casos de uso con dobles en memoria y reloj fijo (`TEST_CLOCK`). | `domain/` y `domain/usecase/Fakes*.kt` |
| Repositorios y base de datos | Room en memoria. Las migraciones se comprueban con `MigrationTestHelper` y los esquemas exportados. | `data/` y `FocusDatabaseMigrationTest` |
| ViewModels | Con `MainDispatcherRule` y estado observado. | `presentation/*ViewModelTest` |
| Pantallas | Compose sobre Robolectric con `createComposeRule`. | `presentation/*ScreensTest` |
| Servicios y alarmas | Robolectric con `ShadowAlarmManager` y `ShadowNotificationManager`. | `service/` |
| Garantías del proyecto | Escanean el código y los recursos, no el comportamiento. | `Rnf3GuardTest`, `InternetGuardTest`, `ThemeContrastTest`, `ReleaseReadinessTest` |

**Mutación de control.** Antes de dar por bueno un cambio en una regla clave, se rompe a propósito y se comprueba que algún test falla. Luego se restaura. Por ejemplo: desmarcar una tarea deja de retirar su XP, y lo detectan cuatro tests. Es la forma de saber que los tests protegen la regla y no solo la cubren.

**Comandos.** Los resultados quedan en `app/build/test-results/testDebugUnitTest/`. El lint se ejecuta con `:app:lintDebug`, y debe quedar sin errores. Los comandos de release están en la sección de publicación.

## Compilar, firmar y publicar

El proyecto compila con Android Studio (JDK 17 o superior) y con el wrapper de Gradle. Desde la raíz:

```bash
./gradlew :app:assembleDebug            # APK de depuración
./gradlew :app:testDebugUnitTest        # tests unitarios
./gradlew :app:lintDebug                # análisis estático
./gradlew :app:assembleRelease          # APK de release, con R8
./gradlew :app:lintVitalRelease         # lint de release
```

En Windows se usa `gradlew.bat`. El APK de release sale sin firmar si no existe `keystore.properties`. Ese fichero no entra en el repositorio, y `.gitignore` lo excluye.

La firma y la publicación siguen un proceso propio. Antes de cada versión:

1. Subir `versionCode` siempre, y `versionName` en `app/build.gradle.kts`.
2. Añadir las notas en `fastlane/metadata/android/es-ES/changelogs/<versionCode>.txt`.
3. Revisar `PRIVACY.md` si cambia un permiso o un dato.
4. Probar el APK de release en un móvil real. R8 puede quitar clases que solo se usan por reflexión.

Las reglas de R8 propias están en `app/proguard-rules.pro`. Si una dependencia nueva usa reflexión, sus reglas van ahí.

## Cómo contribuir

Las contribuciones son bienvenidas, pero el proyecto tiene dos principios que no se negocian. Un cambio que los rompa no se acepta, aunque funcione bien.

1. **Cero rojos y cero culpa (RNF-3).** Ningún color rojizo, ningún texto que reproche, grite o alarme. `Rnf3GuardTest` lo vigila en el código y en los textos.
2. **Todo local (RNF-1).** Sin conexión, sin analytics y sin dependencias que llamen a un servidor. La única excepción es la IA opcional, y `InternetGuardTest` impide ampliarla.

Además:

- **Capas.** El dominio no importa Android, `presentation` no importa `data`, y los cambios de datos pasan por el dominio. Detalle en la sección «Capas y paquetes».
- **Textos.** Todo texto visible va en `res/values/strings.xml`, nunca escrito en el código. Los comentarios y la documentación van en español. Los identificadores, en inglés.
- **Base de datos.** Un cambio de esquema sube `version`, añade una migración y exporta el nuevo JSON de `app/schemas`. La migración se prueba con `MigrationTestHelper`.
- **Tests.** Un cambio de regla lleva su test. Si toca una regla clave, se hace una mutación de control antes de dar el cambio por bueno.
- **Verificación.** Antes de abrir un cambio: `assembleDebug`, `testDebugUnitTest` y `lintDebug`, sin errores.

Un cambio de diseño se propone antes de programarlo. Describe el problema, la regla que cambia y cómo se va a probar.

## Decisiones de diseño

Cada decisión tiene su motivo. Si cambias una, revisa primero el motivo.

| Decisión | Motivo | Dónde se ve en el código |
| --- | --- | --- |
| El XP es un libro de movimientos, no un contador. | Un contador se infla marcando y desmarcando. Con un libro, desmarcar borra el movimiento. | `RewardLedger` |
| La racha tiene un día de gracia por semana y no tiene mensaje de pérdida. | RNF-3: romper una racha no debe castigar. Basta una rutina al día. | `StreakCalculator` |
| Relajar un límite espera a medianoche. Endurecerlo es inmediato. | Decidir en frío, no con el impulso delante. | `LimitChangePolicy` |
| Quitar un límite es inmediato, pero exige un reto matemático. | Evita borrar el límite y recrearlo más holgado. | `AppLimitUseCases` |
| El Cierre de Jornada deduce la noche libre de la hora, no de la alarma. | Una alarma puede retrasarse o no llegar. La noche no debe depender de ella. | `ClosureWindow` |
| No hay ningún rojo en la paleta. Negro, azul eléctrico y morado. | RNF-3. Un rojo activa la alarma sin necesidad. | `Color.kt`, `Rnf3GuardTest` |
| Las tareas no pendientes nunca se marcan como atrasadas. | RNF-3. La lista de hoy no tiene sección de vencidas. | `TaskAgenda` |
| El sonido ambiente se genera en el móvil, sin archivos descargados. | RNF-1. Sin red, sin copias de audio, sin dependencias de contenido. | `domain/audio/` |
| La IA es opcional, usa la clave del usuario y solo envía el título. | RNF-1. La única conexión es la que el usuario activa. | `ChatCompletionsSuggester` |
| El servicio de accesibilidad no puede leer el contenido de la pantalla. | Privacidad: la app solo necesita saber qué app está delante. | `accessibility_service_config.xml` |
| Las mascotas son pixel art recortadas de una hoja del autor. | Un dibujo propio, sin imágenes de terceros. | `res/drawable-nodpi/mascot_*.png` |

Cada decisión está en el código y en sus tests. Si cambias una, revisa primero el motivo y el test que la protege.

## Dónde encontrar cada cosa

| Pregunta | Fichero |
| --- | --- |
| ¿Dónde está la regla que decide si una app se bloquea? | `domain/usecase/EvaluateInterruptionUseCase.kt` |
| ¿Cuánto vale cada acción en XP y monedas? | `domain/reward/RewardRules.kt` |
| ¿Cómo se calcula la racha? | `domain/reward/Streaks.kt` |
| ¿Qué tablas tiene la base de datos? | `data/local/entity/` y `data/local/FocusDatabase.kt` |
| ¿Cómo se migra una versión a otra? | `data/local/FocusDatabase.kt` y `data/local/Migrations.kt` |
| ¿Qué textos ve el usuario? | `res/values/strings.xml` |
| ¿Dónde se conecta cada interfaz con su implementación? | `di/` |
| ¿Qué ocurre al arrancar la app? | `FocusDivergentApp.kt` y `MainActivity.kt` |
| ¿Qué permisos pide la app? | `AndroidManifest.xml` y `PRIVACY.md` |
| ¿Qué garantiza el proyecto y qué lo vigila? | `app/src/test/java/com/focusdivergent/app/` (los tests `*GuardTest`) |
