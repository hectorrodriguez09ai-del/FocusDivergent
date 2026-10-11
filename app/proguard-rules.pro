# Reglas propias de FocusDivergent.
# Room, Hilt, Compose y kotlinx.serialization publican sus reglas consumer. Con ellas, el APK de release (R8 con
# minificación y reducción de recursos) se probó en un móvil real en la 1.0.0: arranque, base de datos, ViewModels de
# Hilt y rutas de navegación serializables (ver Docs/RELEASE.md). Si una dependencia nueva usa reflexión, se añade aquí.
