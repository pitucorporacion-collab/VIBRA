# VIBRA

Aplicación Android para transmitir patrones de vibración entre teléfonos mediante Bluetooth Low Energy (BLE).

## Cómo probar

1. Esperá a que termine el workflow "Build VIBRA APK" en Actions.
2. Descargá el artefacto "VIBRA-debug" de la ejecución más reciente.
3. Instalá la misma APK en ambos teléfonos.
4. En el teléfono receptor, abrí VIBRA y elegí RECEPTOR.
5. En el teléfono emisor, abrí VIBRA y elegí EMISOR.
6. Cuando indique CONECTADO, elegí MEDIUM, HEART BEAT o ZIG ZIG ZIG.

## Patrones

- MEDIUM: un pulso de intensidad media.
- HEART BEAT: un pulso corto e intenso.
- ZIG ZIG ZIG: un pulso más largo e intenso.

Cada toque envía una única señal y el receptor la reproduce una sola vez. Los tres botones usan distintas configuraciones de duración e intensidad; no hay repetición automática. El receptor usa un servicio en primer plano para permitir recepción con la pantalla apagada.

## Requisitos

- Android 8.0 (API 26) o superior.
- Bluetooth activado en ambos teléfonos y cercanía física.
- La recepción en segundo plano depende de permisos, versión de Android y políticas de batería del fabricante. En algunos Samsung puede ser necesario permitir batería sin restricciones para VIBRA.
- La intensidad real depende del motor háptico de cada teléfono.

Esta es una primera versión de prueba. Hay que validar conexión y vibración en dispositivos físicos.
