import sys
import subprocess
import re
import zipfile

with zipfile.ZipFile(sys.argv[1]) as jar:
    checks = {
        "dev/totem/vanillatweaks/client/ContainerSortClient.class": [b"totem:container_sort_client_v1", b"requestSortForScreen", b"getEntrypoints"],
        "dev/totem/vanillatweaks/inventory/ContainerSortService.class": [b"totem:container_sort_server_v1", b"getEntrypoints"],
        "dev/totem/vanillatweaks/mixin/client/AbstractContainerScreenMixin.class": [b"requestSortForScreen"],
    }
    for member, required in checks.items():
        data = jar.read(member)
        for symbol in required:
            if symbol not in data:
                raise SystemExit(f"Missing packaged sort integration: {member}: {symbol.decode()}")
bytecode = subprocess.check_output(["javap", "-c", "-p", "-classpath", sys.argv[1],
    "dev.totem.vanillatweaks.mixin.client.AbstractContainerScreenMixin"], text=True)
for handler in ("totemVanillaTweaks$handleContainerKey", "totemVanillaTweaks$handleSortMouse"):
    match = re.search(r"(?ms)^  private [^\n]* " + re.escape(handler) + r"\(.*?(?=^  (?:private|public|protected)|\Z)", bytecode)
    if match is None or "ContainerSortClient.requestSortForScreen:" not in match.group():
        raise SystemExit(f"Missing packaged owner dispatch in {handler}")
print("Packaged client/server owner-sort dispatch and mouse/key paths present")
