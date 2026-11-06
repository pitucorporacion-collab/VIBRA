# VIBRA

Aplicación Android para transmitir patrones de vibración entre teléfonos y relojes Wear OS mediante Bluetooth Low Energy (BLE).

## APKs que genera GitHub Actions

- **VIBRA-phone-debug**: aplicación original para teléfonos Android. Su código y funcionamiento se mantienen sin cambios.
- **VIBRA-Watch-debug**: aplicación complementaria para relojes Wear OS. Puede funcionar como EMISOR o RECEPTOR.

## Cómo probar entre dos teléfonos

1. Esperá a que termine el workflow "Build VIBRA APKs" en Actions.
2. Descargá el artefacto "VIBRA-phone-debug".
3. Instalá la misma APK en ambos teléfonos.
4. En el teléfono receptor, abrí VIBRA y elegí RECEPTOR.
5. En el teléfono emisor, abrí VIBRA y elegí EMISOR.
6. Cuando indique CONECTADO, elegí MEDIUM, HEART ×1/×2/×3/×4 o ZIG ZIG ZIG.

## Cómo probar con un reloj Wear OS

1. Descargá el artefacto "VIBRA-Watch-debug".
2. Instalá esa APK en el reloj Wear OS.
3. Abrí VIBRA Watch y elegí EMISOR o RECEPTOR.
4. En el teléfono, abrí la VIBRA original y elegí el rol opuesto.
5. Cuando el emisor indique CONECTADO, enviá una señal.

No hace falta reemplazar ni actualizar la APK original del teléfono: el reloj reutiliza el mismo servicio BLE y los mismos códigos de señal.

## Patrones

- MEDIUM: un pulso de intensidad media.
- HEART ×1, ×2, ×3 y ×4: secuencias de latidos.
- ZIG ZIG ZIG: un pulso más largo e intenso.

Cada toque envía una única señal. La intensidad real depende del motor háptico del dispositivo.

## Requisitos y limitaciones

- Teléfonos: Android 8.0 (API 26) o superior.
- Relojes: Wear OS 3 o superior (API 30+), con Bluetooth LE y vibrador.
- Bluetooth activado en ambos dispositivos y cercanía física.
- El receptor usa un servicio en primer plano para intentar mantener la recepción con la pantalla apagada. El comportamiento en segundo plano depende de la versión de Wear OS, permisos y políticas de batería del fabricante.
- La conexión BLE y la vibración deben validarse en el modelo físico del reloj antes de considerar la integración terminada.
