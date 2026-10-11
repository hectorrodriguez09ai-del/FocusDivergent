<div align="center">

# FocusDivergent

**Protect your attention without being punished for it. · Protege tu atención sin que nadie te castigue por ello.**

![Status: BETA](https://img.shields.io/badge/status-BETA-blueviolet)
![Version 1.0.0-beta](https://img.shields.io/badge/version-1.0.0--beta-448AFF)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![100% local](https://img.shields.io/badge/data-100%25%20local-success)
![License GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)

[English](#english) · [Español](#español)

</div>

---

# English

FocusDivergent is an Android app that helps you protect your attention and get started on things. It is made for
**neurodivergent people**, and it is centred on **ADHD and high abilities (gifted)**. It works without an internet connection,
and your data never leaves your phone.

**What it does**

- **Limits per app** with gentle friction: you choose how much time each app gets each day.
- **Pick for me**: one suggested task that fits your energy and the time you have.
- **2-minute Launchpad** to start without distractions.
- **Tasks, obligations and routines**, with calm reminders and no red alerts.
- **Micro-steps**: split a task into 5-minute steps, shown one at a time.
- **Quick idea capture** from the notification bar or Quick Settings.
- **Visual timer**, Pomodoro and ambient sounds generated on the phone.
- **Day closure** at 21:30: a calm summary, and unfinished tasks move to tomorrow.

**Beta.** Version 1.0.0 is in beta. It has been tested on a few devices, so expect rough edges. Your feedback is welcome:
open an issue in this repository.

**Install (beta)**

1. Get the file `FocusDivergent-1.0.0-beta.apk` from the person who shared it with you.
2. Open it. If Android blocks it, allow installs from that source in *Settings*, go back and tap **Install**.
3. Open FocusDivergent and follow the welcome screen. It asks for three permissions and sends you to each Android screen:
   usage access, display over other apps and the accessibility service.
4. Do not "force stop" the app from Android settings: the protection stops until you enable it again.

**Permissions, in short.** The accessibility service only learns which app is in front; it cannot read the screen or what
you type. Usage access measures your time in each app. Display over other apps draws the pause screen. Notifications and
exact alarms make reminders arrive on time. Internet is used only by an optional AI feature, which is off unless you add
your own key. The full list and the reason for each is in [PRIVACY.md](PRIVACY.md).

**Privacy.** No accounts, no analytics, no ads. Your data stays on your device, and backups are off.

**Build from source**

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests
```

Requirements: Android Studio with JDK 17 or newer. On Windows, use `gradlew.bat`.

**License.** [GNU General Public License v3.0](LICENSE). Copyright (C) 2026 Héctor Rodríguez Ortiz.

---

# Español

FocusDivergent es una app de Android que te ayuda a proteger tu atención y a empezar cosas. Está pensada para personas
**neurodivergentes**, y se centra en el **TDAH y las altas capacidades**. Funciona sin conexión a internet, y tus datos
nunca salen de tu móvil.

**Qué hace**

- **Límites por app** con una fricción amable: eliges cuánto tiempo tiene cada app al día.
- **Elige por mí**: una sola tarea sugerida según tu energía y el tiempo que tienes.
- **Lanzadera de 2 minutos** para empezar sin distracciones.
- **Tareas, obligaciones y rutinas**, con avisos tranquilos y sin alertas en rojo.
- **Micro-pasos**: divide una tarea en pasos de 5 minutos, vistos uno a uno.
- **Captura rápida de ideas** desde la barra de notificaciones o los Ajustes rápidos.
- **Temporizador visual**, Pomodoro y sonidos ambiente generados en el móvil.
- **Cierre de jornada** a las 21:30: un resumen tranquilo, y las tareas sin terminar pasan a mañana.

**Beta.** La versión 1.0.0 está en beta. Solo se ha probado en pocos dispositivos, así que habrá asperezas. Tu opinión es
bienvenida: abre una incidencia en este repositorio.

**Instalar (beta)**

1. Consigue el fichero `FocusDivergent-1.0.0-beta.apk` de la persona que te lo ha pasado.
2. Ábrelo. Si Android lo bloquea, permite instalar desde esa fuente en *Ajustes*, vuelve atrás y pulsa **Instalar**.
3. Abre FocusDivergent y sigue la pantalla de bienvenida. Pide tres permisos y te lleva a cada pantalla de Android:
   acceso al uso de apps, mostrar sobre otras apps y el servicio de accesibilidad.
4. No «fuerces la detención» de la app desde los ajustes de Android: la protección se para hasta que la vuelvas a activar.

**Permisos, en resumen.** El servicio de accesibilidad solo sabe qué app está delante; no puede leer la pantalla ni lo que
escribes. El acceso al uso de apps mide tu tiempo en cada una. Mostrar sobre otras apps dibuja la pantalla de pausa.
Las notificaciones y las alarmas exactas hacen que los avisos lleguen a su hora. Internet solo lo usa una función opcional
de IA, apagada hasta que pongas tu propia clave. La lista completa y el motivo de cada permiso está en
[PRIVACY.md](PRIVACY.md).

**Privacidad.** Sin cuentas, sin analítica y sin anuncios. Tus datos se quedan en tu dispositivo, y las copias de seguridad
están desactivadas.

**Compilar desde el código**

```bash
./gradlew :app:assembleDebug        # APK de depuración
./gradlew :app:testDebugUnitTest    # tests unitarios
```

Requisitos: Android Studio con JDK 17 o superior. En Windows, usa `gradlew.bat`.

**Licencia.** [GNU General Public License v3.0](LICENSE). Copyright (C) 2026 Héctor Rodríguez Ortiz.
