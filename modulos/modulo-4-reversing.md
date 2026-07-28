# Módulo 4: Malware & Old School Reversing

> **Nivel:** Level 0 to Hero · **Categoría:** Reversing / Binarios
> **Requisitos previos:** Módulos 1-3 recomendados. Saber compilar con `gcc` ayuda, pero te guiamos.
>
> ⚠️ **Entorno seguro:** Todos los binarios de este módulo se generan a partir de **código fuente en C benigno** que tú mismo compilas. **No hay malware real** en ningún reto: son "crackmes", programas-rompecabezas hechos para aprender. Aun así, adquiere el hábito de analizar binarios desconocidos dentro de una **máquina virtual aislada**.

---

## 1. Introducción y Modelo Mental

### La Analogía del Mundo Real

Imagina que te dan un reloj mecánico sellado que solo se abre si giras las manecillas hasta una hora exacta y secreta. No tienes el manual ni la combinación. ¿Qué haces? Lo abres con cuidado, observas los engranajes, sigues cómo encajan las piezas y **deduces** qué posición hace *clic*. No creaste el reloj, pero al entender su mecanismo, descubres su secreto.

**Reversing** (ingeniería inversa) es eso con programas: te dan un binario —el reloj sellado— sin su código fuente, y tú desmontas su lógica para entender qué hace y encontrar la "combinación" (una contraseña, una licencia, una flag). Un **crackme** es un reloj diseñado a propósito para que practiques abrirlo. No rompes leyes ni sistemas: entrenas tu capacidad de leer la máquina por dentro.

### Impacto Real

Cuando aparece un malware nuevo, alguien tiene que abrirlo y entender qué hace: a qué servidor llama, qué cifra, cómo se propaga. Ese alguien es un **analista de malware / ingeniero inverso**, y su trabajo permite crear las firmas de los antivirus y detener campañas. La misma habilidad sirve para auditar software sin código fuente, encontrar puertas traseras y estudiar vulnerabilidades. Es de las especialidades mejor pagadas y más respetadas de la ciberseguridad.

### Objetivo del Módulo

Al terminar sabrás **analizar un binario sin su código fuente**: extraer cadenas legibles con `strings`, inspeccionar la lógica con un desensamblador/decompilador (Ghidra), depurar la ejecución paso a paso con `gdb`, y **derrotar comprobaciones de contraseña** basadas en comparación de cadenas, operaciones XOR y control de flujo.

---

## 2. Glosario Rápido

| Término | Definición en una frase |
|---|---|
| **Binario / Ejecutable** | El archivo de máquina que resulta de compilar el código fuente; lo que la CPU corre. |
| **Reversing** | Analizar un programa sin su fuente para entender su lógica. |
| **Crackme** | Programa-rompecabezas hecho para practicar reversing legalmente. |
| **Disassembler** | Herramienta que traduce el binario a instrucciones de ensamblador legibles. |
| **Decompiler** | Va un paso más allá: reconstruye pseudo-C a partir del ensamblador (Ghidra). |
| **strings** | Utilidad que extrae el texto ASCII embebido en un binario. |
| **XOR** | Operación reversible muy usada para ofuscar datos con una clave. |

---

## 3. Cheat Sheet: Caja de Herramientas y Comandos

### Tabla de Herramientas

| Herramienta | Función | Dónde obtenerla |
|---|---|---|
| **gcc** | Compilar el código C de los retos a un binario. | `sudo apt install build-essential` |
| **Ghidra** | Decompilador gratuito de la NSA; convierte binario en pseudo-C. | https://ghidra-sre.org/ |
| **strings** | Extraer texto legible de un binario. | Incluido en `binutils` (Linux/macOS). |
| **gdb** | Depurador: ejecuta paso a paso, inspecciona memoria y registros. | `sudo apt install gdb` |
| **objdump** | Desensamblar desde la terminal, ver secciones. | Incluido en `binutils`. |
| **ltrace / xxd** | Ver llamadas a librerías / volcar bytes en hex. | `sudo apt install ltrace xxd` |

### Top Comandos / Sintaxis

```bash
# 1. Compilar un crackme desde su fuente C
gcc -o crackme crackme.c            # -o define el nombre del binario de salida

# 2. Buscar cadenas legibles (¡la contraseña suele estar aquí!)
strings crackme                     # imprime todo el texto ASCII embebido
strings crackme | grep -i flag      # filtra por la palabra flag

# 3. Ver llamadas a librerías en tiempo de ejecución (p. ej. strcmp)
ltrace ./crackme                    # muestra strcmp("tu_input", "clave_real")

# 4. Depurar con gdb y poner un breakpoint en main
gdb ./crackme
(gdb) break main                    # detén la ejecución al entrar a main
(gdb) run                           # ejecuta hasta el breakpoint
(gdb) disassemble                   # muestra el ensamblador de la función
(gdb) info registers               # ver el estado de los registros

# 5. Volcar bytes en hexadecimal (útil para claves XOR embebidas)
xxd crackme | grep -A2 "clave"      # ver bytes alrededor de un patrón

# 6. En Ghidra (GUI): File > Import File > (auto-analyze) > doble clic en 'main'
#    La ventana 'Decompile' muestra pseudo-C legible de la función.
```

> **Consejo:** Antes de abrir Ghidra (que tarda en analizar), prueba SIEMPRE `strings`. Muchos retos fáciles guardan la flag en texto plano dentro del binario.

---

## 4. Tutorial Guiado — "Laboratorio 0"

### El Escenario

"Botica Digital" distribuye un programa de activación de licencias. Alguien perdió la contraseña de administrador y solo queda el ejecutable. Tu misión de práctica: recuperar la contraseña analizando el binario.

### Paso 1 — Crea el código fuente del crackme

Crea `lab0.c`:

```c
#include <stdio.h>
#include <string.h>

int main() {
    char input[64];
    const char *clave = "abre_sesamo";   // "escondida" en el binario
    printf("Introduce la clave de administrador: ");
    if (scanf("%63s", input) != 1) return 1;

    if (strcmp(input, clave) == 0) {
        printf("Acceso concedido. Flag: FLAG{mi_primer_crackme}\n");
    } else {
        printf("Clave incorrecta.\n");
    }
    return 0;
}
```

### Paso 2 — Compílalo

```bash
gcc -o lab0 lab0.c
```

**¿Por qué?** `gcc` traduce tu C legible a instrucciones de máquina. El resultado, `lab0`, ya no muestra el código fuente... pero sus *secretos* siguen ahí dentro. Vamos a probarlo.

### Paso 3 — Ejecútalo "a ciegas"

```bash
./lab0
```

Escribe cualquier cosa: dice "Clave incorrecta". Sin el fuente, un usuario normal se rendiría. Tú no.

### Paso 4 — El truco más viejo: `strings`

```bash
strings lab0
```

Entre la salida verás líneas como:

```
Introduce la clave de administrador:
abre_sesamo
Acceso concedido. Flag: FLAG{mi_primer_crackme}
Clave incorrecta.
```

**¿Por qué ocurrió?** Las cadenas de texto ("literales") se guardan **tal cual** en una sección del binario. `strings` simplemente las lee todas. La contraseña `abre_sesamo` estaba escrita en el código, así que sobrevivió a la compilación en texto plano.

### Paso 5 — Confírmalo

```bash
./lab0
Introduce la clave de administrador: abre_sesamo
```

Respuesta: `Acceso concedido. Flag: FLAG{mi_primer_crackme}`.

### Paso 6 — Mira "por debajo" con ltrace (bonus)

```bash
ltrace ./lab0
```

Cuando escribas cualquier clave, verás la línea:

```
strcmp("lo_que_escribiste", "abre_sesamo") = -1
```

**¿Por qué?** `ltrace` intercepta las llamadas a funciones de librería. El programa usa `strcmp` para comparar tu entrada con la clave real, y `ltrace` te muestra **ambos argumentos**, incluida la respuesta correcta, sin que tengas que buscarla.

🎉 **Primer crackme resuelto** con la técnica más fundamental del reversing: mirar las cadenas y las llamadas.

---

## 5. Especificación de Retos Progresivos

> **Nota de despliegue:** El instructor compila cada fuente C y entrega **solo el binario** al alumno (nunca el `.c`). Compilar sugerido: `gcc -o reto reto.c`. Todo el código es benigno y seguro.

---

### 🟢 Nivel 1 — "La Cerradura Parlanchina" (50 pts)

**Historia / Contexto:** Un ejecutable de licencia guarda su clave... en texto plano. Recupérala.

**Especificación Técnica de Despliegue** — `reto1.c`:

```c
#include <stdio.h>
#include <string.h>

int main() {
    char input[64];
    const char *clave = "licencia-2024-vip";
    printf("Clave de licencia: ");
    if (scanf("%63s", input) != 1) return 1;
    if (strcmp(input, clave) == 0)
        printf("FLAG{strings_es_tu_amigo}\n");
    else
        printf("Denegado.\n");
    return 0;
}
```

**Formato de la Flag:** `FLAG{strings_es_tu_amigo}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** No necesitas ejecutar nada complicado. Todo el texto que un programa imprime o compara vive *dentro* del archivo. ¿Qué comando lista ese texto?
- **Pista 2 (−10%):** Ejecuta `strings reto1` y busca algo que parezca una clave o una flag.
- **Pista 3 (−30%):** `strings reto1 | grep -Ei "flag|licencia"`. Verás la clave `licencia-2024-vip` y la flag directamente.

**Writeup Oficial:**
1. `strings reto1` lista todas las cadenas embebidas.
2. Aparecen `licencia-2024-vip` (la clave) y `FLAG{strings_es_tu_amigo}` (la flag imprimible).
3. Puedes leer la flag directo, o ejecutar `./reto1` e introducir `licencia-2024-vip` para que la imprima.

---

### 🟡 Nivel 2 — "Espejo Invertido" (100 pts)

**Historia / Contexto:** El desarrollador leyó sobre `strings` y decidió "esconder" la clave: la guarda **XOR-eada** con un byte fijo, y la desencripta en memoria solo al comparar. Ya no la verás en texto plano. Tendrás que revertir el XOR.

**Especificación Técnica de Despliegue** — `reto2.c`:

```c
#include <stdio.h>
#include <string.h>

int main() {
    char input[64];
    // clave real "sombra" XOR 0x2A, almacenada ya ofuscada:
    unsigned char enc[] = {0x59,0x45,0x47,0x48,0x58,0x4B,0x00}; // 's'^0x2A, 'o'^0x2A, ...
    char clave[8];
    for (int i = 0; enc[i] != 0x00; i++)
        clave[i] = enc[i] ^ 0x2A;      // desofusca en memoria
    clave[6] = '\0';

    printf("Contraseña: ");
    if (scanf("%63s", input) != 1) return 1;
    if (strcmp(input, clave) == 0)
        printf("FLAG{xor_no_te_salva}\n");
    else
        printf("Nope.\n");
    return 0;
}
```

**Formato de la Flag:** `FLAG{xor_no_te_salva}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** `strings` ya no muestra la clave: está codificada. Pero la clave *tiene* que existir en claro en algún momento: cuando el programa la compara. ¿Cómo espías ese instante?
- **Pista 2 (−10%):** Dos caminos: (a) `ltrace ./reto2` para ver el `strcmp` con la clave ya descifrada; (b) en Ghidra, busca el arreglo de bytes y la constante XOR.
- **Pista 3 (−30%):** Con `ltrace` verás `strcmp("tu_input", "sombra")`. O revierte a mano: cada byte de `enc` XOR `0x2A` da la clave. Introduce `sombra`.

**Writeup Oficial:**
1. `strings` no revela la clave (está ofuscada con XOR).
2. Camino fácil — `ltrace ./reto2`: al comparar, muestra `strcmp("loquesea", "sombra") = ...`. La clave descifrada es `sombra`.
3. Camino "puro" — en Ghidra ves el arreglo `{0x59,0x45,...}` y la operación `^ 0x2A`. Aplica CyberChef *XOR* con clave `0x2A` a esos bytes → `sombra`.
4. Ejecuta `./reto2`, escribe `sombra` → `FLAG{xor_no_te_salva}`. Lección: ofuscar (XOR) no es cifrar; la clave se revela en runtime.

---

### 🔴 Nivel 3 — "El Laberinto de las Condiciones" (200 pts)

**Historia / Contexto:** No hay comparación con una cadena única. El programa valida la flag **carácter por carácter** con una serie de comprobaciones matemáticas (control de flujo). No aparece ninguna cadena legible: hay que **reconstruir la flag desde la lógica**. Combina lectura de decompilado + razonamiento.

**Especificación Técnica de Despliegue** — `reto3.c`:

```c
#include <stdio.h>
#include <string.h>

// Verifica una flag de 12 caracteres mediante comprobaciones por carácter.
// No se almacena la flag como cadena: se define por sus restricciones.
int check(const char *s) {
    if (strlen(s) != 12) return 0;
    if (s[0]  != 'r')                return 0;
    if (s[1]  != s[0] + 1)           return 0;  // 's'
    if (s[2]  != '3')                return 0;
    if (s[3]  != 'v')                return 0;
    if (s[4]  != (s[3] ^ 0x12))      return 0;  // 'd'
    if (s[5]  != '_')                return 0;
    if (s[6]  != 'l')                return 0;
    if (s[7]  != '0')                return 0;
    if (s[8]  != 'g')                return 0;
    if (s[9]  != '1')                return 0;
    if (s[10] != 'c')                return 0;
    if (s[11] != s[6])               return 0;  // 'l'
    return 1;
}

int main() {
    char input[64];
    printf("Clave interna: ");
    if (scanf("%63s", input) != 1) return 1;
    if (check(input))
        printf("Correcto. FLAG{%s}\n", input);   // la flag se arma con tu input válido
    else
        printf("Incorrecto.\n");
    return 0;
}
```

> La clave interna válida es `rs3vd_l0g1cl`, así que la flag final es `FLAG{rs3vd_l0g1cl}`.

**Formato de la Flag:** `FLAG{rs3vd_l0g1cl}`

**Sistema de Pistas:**
- **Pista 1 (Gratis):** No hay ninguna cadena que copiar. La contraseña *se describe* mediante condiciones sobre cada carácter. ¿Puedes leer esas condiciones una por una y deducir cada letra?
- **Pista 2 (−10%):** Abre el binario en Ghidra y decompila la función `check`. Verás comparaciones tipo `s[0] == 'r'`, `s[4] == (s[3] ^ 0x12)`, etc. Resuélvelas en orden.
- **Pista 3 (−30%):** Ve carácter a carácter: `s[0]='r'`, `s[1]='r'+1='s'`, `s[2]='3'`, `s[3]='v'`, `s[4]='v'^0x12='d'`, `s[5]='_'`, ... `s[11]=s[6]='l'`. Concatena: `rs3vd_l0g1cl`.

**Writeup Oficial:**
1. `strings` no ayuda: no hay flag embebida como cadena.
2. En Ghidra, decompila `check`. Cada `if` fija un carácter, directa o relativamente:
   - `s[0]='r'`; `s[1]=s[0]+1='s'`; `s[2]='3'`; `s[3]='v'`; `s[4]=s[3]^0x12='d'`; `s[5]='_'`;
   - `s[6]='l'`; `s[7]='0'`; `s[8]='g'`; `s[9]='1'`; `s[10]='c'`; `s[11]=s[6]='l'`.
3. Resolviendo en orden: `rs3vd_l0g1cl`.
4. Ejecuta `./reto3`, escribe `rs3vd_l0g1cl`. El programa la envuelve: `FLAG{rs3vd_l0g1cl}`.
5. Lección: cuando no hay cadena que robar, la contraseña *es* la lógica; se reconstruye resolviendo el sistema de restricciones. (Nivel bonus: automatízalo con un SAT solver como `z3`.)

---

## 6. "¡Me Atoré!" — Errores Comunes y Resolución de Problemas

**1. "`gcc` no está instalado / el binario no corre."**
Instala las herramientas de compilación: `sudo apt install build-essential`. Si el binario "no ejecuta", dale permisos con `chmod +x reto1` y córrelo con `./reto1` (la `./` es obligatoria en Linux para binarios del directorio actual). En macOS con chip Apple, compila y ejecuta en la misma máquina para evitar incompatibilidades de arquitectura.

**2. "Ghidra abre pero no encuentro la lógica / me abruma el ensamblador."**
No leas el ensamblador crudo: usa la ventana **Decompile** (pseudo-C) a la derecha. Navega por el árbol de *Functions* en la izquierda y entra a `main` o `check`. Si Ghidra tarda, deja que termine el *auto-analysis* la primera vez. Y recuerda el atajo del pobre: prueba `strings` y `ltrace` antes de abrir Ghidra.

**3. "Revertí el XOR pero la clave no funciona."**
Errores típicos: (a) aplicaste el XOR con la clave equivocada —revisa la constante exacta en el decompilado (aquí `0x2A`); (b) incluiste el byte nulo `0x00` terminador en la conversión —descártalo; (c) confundiste hex con decimal. Verifica byte por byte: `0x59 ^ 0x2A = 0x73 = 's'`. Si usas CyberChef, asegúrate de marcar el formato de entrada correcto (Hex) en la operación *XOR*.

---

> **Nota final del mentor:** El reversing enseña la verdad más humilde de la seguridad: **si el usuario tiene el binario, tiene todos sus secretos**; solo es cuestión de tiempo y paciencia. Ocultar una clave en el código (`strings`), ofuscarla con XOR o esconderla en la lógica solo sube el costo, nunca lo vuelve imposible. Por eso los secretos reales viven en servidores, no en clientes. Completaste los cuatro módulos: pasaste de *Level 0* a saber jailbreakear IAs, ejecutar RCE, exfiltrar datos y abrir binarios. Ya no eres un novato. Bienvenido al oficio. 🏆
