# Módulos Didácticos — "Level 0 to Hero"

Índice de los módulos de entrenamiento CTF. Cada módulo es autocontenido y sigue
la misma estructura pedagógica: modelo mental → glosario → cheat sheet → tutorial
guiado → 3 retos progresivos (50/100/200 pts) → resolución de problemas.

| # | Módulo | Categoría | Herramientas clave | Enlace |
|---|--------|-----------|--------------------|--------|
| 1 | Jailbreaks & Secuestro de Agentes | IA / LLM Security | Ollama, curl | [modulo-1-jailbreaks.md](./modulo-1-jailbreaks.md) |
| 2 | Exploitation (RCE) | Web / Exploitation | Docker, Netcat, Burp | [modulo-2-rce.md](./modulo-2-rce.md) |
| 3 | Exfiltration (Bypassing Networks) | Redes / Forense | Wireshark, tcpdump, CyberChef | [modulo-3-exfiltration.md](./modulo-3-exfiltration.md) |
| 4 | Malware & Old School Reversing | Reversing / Binarios | Ghidra, gdb, strings | [modulo-4-reversing.md](./modulo-4-reversing.md) |

> ⚠️ **Recordatorio de seguridad:** Todos los retos son 100% sintéticos y están
> pensados para ejecutarse **solo** en Docker local, máquinas virtuales aisladas
> o archivos locales. Nunca apliques estas técnicas contra sistemas que no sean
> tuyos o para los que no tengas autorización explícita por escrito.

## Ruta sugerida

1. Empieza por el **Módulo 1** aunque no tengas experiencia: no requiere nada previo.
2. Haz el **"Laboratorio 0"** (tutorial guiado) de cada módulo antes de tocar los retos.
3. Resuelve los retos en orden (Nivel 1 → 2 → 3). Usa las pistas solo si te atoras.
4. Lee siempre el writeup **después** de intentarlo tú: aprenderás el doble.
