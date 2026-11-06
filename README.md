# VIBRA

Aplicación Android para enviar y recibir señales hápticas entre teléfonos y relojes Wear OS mediante Bluetooth Low Energy (BLE).

## APKs que genera GitHub Actions

- **VIBRA-phone-debug**: aplicación para teléfonos Android.
- **VIBRA-Watch-debug**: aplicación simplificada para relojes Wear OS.

## Funcionamiento

Ambas aplicaciones abren directamente la pantalla de botones y mantienen activa la recepción en segundo plano mediante un servicio en primer plano. No hay que elegir entre EMISOR y RECEPTOR: cada dispositivo puede enviar y recibir.

- El teléfono conserva sus controles MEDIUM, HEART ×1/×2/×3/×4 y ZIG ZIG ZIG, más ALERTA.
- El reloj usa botones grandes y una lista desplazable: HEART ×1/×2/×3/×4, MEDIUM, ZIG ZIG ZIG y ALERTA.
- Las señales recibidas producen la vibración correspondiente y una alerta visual.
- En el reloj, la alerta puede abrirse a pantalla completa y permanece hasta tocar **TOCAR PARA CERRAR**.
- En el teléfono, la notificación abre una alerta que se cierra tocando **CERRAR**.
- HEART ×2/×3/×4 repite el patrón de latido con 500 ms de separación entre repeticiones.
- Las señales recibidas pueden retransmitirse una vez a otros dispositivos conectados, evitando el rebote al emisor original.

## Instalación y prueba

1. Esperá a que termine el workflow **Build VIBRA APKs**.
2. Descargá el artefacto **VIBRA-phone-debug** e instalalo en los teléfonos.
3. Descargá el artefacto **VIBRA-Watch-debug** e instalalo en el reloj Wear OS.
4. Abrí VIBRA en cada dispositivo, concedé los permisos Bluetooth y de notificaciones y dejá Bluetooth activado.
5. Los dispositivos cercanos con VIBRA activa se detectan por BLE; probá enviar una señal desde cualquiera de ellos.

## Requisitos y limitaciones

- Teléfonos: Android 8.0 (API 26) o superior.
- Relojes: Wear OS 3 o superior (API 30+), con Bluetooth LE y vibrador.
- Bluetooth activado en todos los dispositivos.
- El servicio en primer plano mantiene la recepción mientras la aplicación no está en pantalla. Android/Wear OS puede limitar conexiones, notificaciones a pantalla completa y ejecución en segundo plano según permisos, versión y políticas de batería.
- La retransmisión requiere que la aplicación que actúa como puente mantenga activa su actividad de conexión BLE.
- La conexión BLE, la alerta a pantalla completa y la vibración deben validarse en el Galaxy Watch4 y en los teléfonos físicos antes de dar la integración por terminada.
