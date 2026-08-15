from pathlib import Path
import re

p = Path(r"app/src/main/kotlin/com/music/echo/ui/screens/WelcomeDialog.kt")
t = p.read_text(encoding="utf-8", errors="replace")
t = t.replace("Spacer(modifier = Modifier.height(4.dp))", "Spacer(Modifier.height(4.dp))")
t = re.sub(r'else "Atr.s"', 'else "Atras"', t)
p.write_text(t, encoding="utf-8")
print("done")
for i, line in enumerate(t.splitlines()):
    if "Spacer" in line or "Atr" in line:
        print(i + 1, repr(line))
