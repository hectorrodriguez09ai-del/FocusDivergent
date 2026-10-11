# Privacy policy · Política de privacidad

*Last updated / Última actualización: 11 October 2026 · 11 de octubre de 2026 · version / versión 1.0.0 (beta)*

[English](#english) · [Español](#español)

---

# English

**In short:** FocusDivergent has no accounts, no servers, no analytics and no advertising. What you write and what you do in the
app stays on your phone. There is one optional feature that uses the internet, and only if you provide your own key.

## What data the app stores, and where

All data is stored **only on your device**, in the app's private storage: your tasks, routines and steps, your notes and ideas,
your app limits, your XP and coins, and your settings. Nothing is sent to anyone. Android backups are disabled
(`allowBackup=false`), so nothing goes to cloud backups either. If you uninstall the app, all of its data is deleted.

## Permissions and what they are used for

| Permission | What it is used for | What it does NOT do |
|---|---|---|
| **Accessibility service** | To know which app has just come to the foreground, so the pause screen can appear as soon as a limit you set is reached. | It cannot read the content of the screen or what you type (`canRetrieveWindowContent=false`). It only listens to window changes. It sends nothing. |
| **Usage access** (`PACKAGE_USAGE_STATS`) | To measure how long you spend in each app, to apply your daily limits and to build the day summary. | The data is computed on the device and does not leave it. |
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | To draw the pause screen, the Reality Check, the Launchpad and Obligation alerts on top of another app. | It only draws FocusDivergent's own screens. |
| **Foreground services** (`FOREGROUND_SERVICE`, `…SPECIAL_USE`, `…MEDIA_PLAYBACK`) | To keep the protection running reliably and to keep the ambient sound playing with the app closed. | |
| **Notifications** (`POST_NOTIFICATIONS`) | For Obligation alerts, routine reminders, the timer and the day summary. | They never repeat or escalate. Each channel can be muted in Android settings. The protection works without this permission. |
| **Exact alarms** (`SCHEDULE_EXACT_ALARM`, optional) | To fire alerts at the exact minute. Without it, alerts can be delayed by a few minutes. | |
| **Run at startup** (`RECEIVE_BOOT_COMPLETED`) | To restore the protection, alarms and reminders after a reboot or an app update. | |
| **Internet** (`INTERNET`) | **Only** for the optional feature «Steps with your own AI» (see below). | With no key configured, the app opens no connection at all. |

## The only feature that uses the internet: «Steps with your own AI»

It is optional and **off by default**. In *Blocks → Steps with your own AI* you enter the `https` address of a service compatible
with the OpenAI «chat completions» API, a model name and **your own key**.

- **What is sent:** the **title of the task** you choose, and your key in the request header so the service can identify you.
  Nothing else: no notes, no other tasks, no data from your phone.
- **When:** only when you press «Suggest steps with my AI». Before the first send to each server, the app shows exactly what will
  be sent and to whom, and sends nothing until you accept. If you change the server, it asks again.
- **Who receives it:** the service you chose. How that service handles data is governed by its own privacy policy, not this one.
  FocusDivergent does not receive a copy.
- **Your key:** encrypted with the Android Keystore (AES-GCM) and never shown again. «Remove key and turn off the feature» deletes it
  together with the permission you gave. Only encrypted connections (`https`) are used, and redirects are not followed, so the key
  cannot be sent to a different server.

## What we do not do

We do not collect personal data, there are no advertising identifiers, we do not include analytics or advertising libraries, and we
do not sell or share anything. We have nothing to share.

## Changes and contact

If this policy changes, it will be updated here and noted in the release notes of the version concerned. For questions or to report
a privacy problem, open an issue in the project repository.

---

# Español

**En resumen:** FocusDivergent no tiene cuentas, ni servidores, ni analítica, ni publicidad. Lo que escribes y lo que haces en la
app se queda en tu móvil. Hay una única función opcional que usa internet, y solo si tú pones tu propia clave.

## Qué datos guarda la app y dónde

Todos los datos se guardan **solo en tu dispositivo**, en el almacenamiento privado de la app: tus tareas, rutinas y pasos, tus
notas e ideas, tus límites de uso, el XP y las monedas, y los ajustes. No se envía nada a nadie. Las copias de seguridad de Android
están desactivadas (`allowBackup=false`), así que tampoco viaja nada a copias en la nube. Si desinstalas la app, se borran todos sus datos.

## Permisos y para qué se usan

| Permiso | Para qué se usa | Qué NO hace |
|---|---|---|
| **Servicio de accesibilidad** | Saber qué app acaba de pasar a primer plano, para mostrar la pantalla de pausa en cuanto se alcanza un límite que tú configuraste. | No puede leer el contenido de la pantalla ni lo que escribes (`canRetrieveWindowContent=false`). Solo escucha cambios de ventana. No envía nada. |
| **Acceso al uso de apps** (`PACKAGE_USAGE_STATS`) | Medir cuánto tiempo pasas en cada app, aplicar tus límites diarios y construir el resumen del día. | Los datos se calculan en el dispositivo y no salen de él. |
| **Mostrar sobre otras apps** (`SYSTEM_ALERT_WINDOW`) | Dibujar la pantalla de pausa, el Reality Check, la Lanzadera y los avisos de Obligaciones encima de otra app. | Solo dibuja pantallas propias de FocusDivergent. |
| **Servicios en primer plano** (`FOREGROUND_SERVICE`, `…SPECIAL_USE`, `…MEDIA_PLAYBACK`) | Mantener la protección activa de forma fiable y que el sonido ambiente siga con la app cerrada. | |
| **Notificaciones** (`POST_NOTIFICATIONS`) | Avisos de Obligaciones, recordatorios de rutinas, temporizador y resumen del Cierre de Jornada. | Nunca se repiten ni escalan. Cada canal se puede silenciar desde los ajustes de Android. La protección funciona sin este permiso. |
| **Alarmas exactas** (`SCHEDULE_EXACT_ALARM`, opcional) | Lanzar los avisos al minuto exacto. Sin él, pueden retrasarse unos minutos. | |
| **Iniciar al arrancar** (`RECEIVE_BOOT_COMPLETED`) | Restaurar la protección, las alarmas y los recordatorios tras reiniciar el móvil o actualizar la app. | |
| **Internet** (`INTERNET`) | **Solo** para la función opcional «Pasos con tu propia IA» (ver abajo). | Sin una clave configurada, la app no abre ninguna conexión. |

## La única función que usa internet: «Pasos con tu propia IA»

Es opcional y está **apagada de serie**. En *Bloqueos → Pasos con tu propia IA* introduces la dirección `https` de un servicio
compatible con la API de «chat completions» de OpenAI, un nombre de modelo y **tu propia clave**.

- **Qué se envía:** el **título de la tarea** que elijas, y tu clave en la cabecera de la petición para que el servicio te identifique.
  Nada más: ni notas, ni otras tareas, ni datos del móvil.
- **Cuándo:** solo cuando pulsas «Sugerir pasos con mi IA». Antes del primer envío a cada servidor, la app te muestra exactamente qué
  se enviará y a quién, y no envía nada hasta que aceptes. Si cambias de servidor, vuelve a preguntar.
- **Quién lo recibe:** el servicio que elegiste. Cómo trata ese servicio los datos se rige por su propia política de privacidad, no
  por esta. FocusDivergent no recibe ninguna copia.
- **Tu clave:** se guarda cifrada con el Keystore de Android (AES-GCM) y no se vuelve a mostrar. «Quitar la clave y apagar la función»
  la borra junto con el permiso dado. Solo se usan conexiones cifradas (`https`) y no se siguen redirecciones, así que la clave no puede
  enviarse a otro servidor.

## Lo que no hacemos

No recogemos datos personales, no hay identificadores publicitarios, no incluimos bibliotecas de analítica ni de publicidad, y no
vendemos ni compartimos nada: no tenemos nada que compartir.

## Cambios y contacto

Si esta política cambia, se actualizará aquí y se indicará en las notas de la versión correspondiente. Para dudas o para señalar un
problema de privacidad, abre una incidencia en el repositorio del proyecto.
