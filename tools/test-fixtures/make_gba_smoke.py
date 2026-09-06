"""Build an original diagnostic ROM, without third-party game data or logo assets."""
import argparse
from pathlib import Path
import struct
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("output", type=Path)
args = parser.parse_args()
with tempfile.TemporaryDirectory() as directory:
    obj = Path(directory) / "smoke.o"
    subprocess.run(["clang", "--target=arm-none-eabi", "-mcpu=arm7tdmi", "-c",
                    str(Path(__file__).with_name("gba-smoke.s")), "-o", str(obj)], check=True)
    elf = obj.read_bytes()
    header = struct.unpack_from("<16sHHIIIIIHHHHHH", elf)
    sections = [struct.unpack_from("<IIIIIIIIII", elf, header[6] + i * header[11]) for i in range(header[12])]
    strings = sections[header[13]]
    names = elf[strings[4]:strings[4] + strings[5]]
    text = next(section for section in sections if names[section[0]:].split(b"\0", 1)[0] == b".text")
    rom = bytearray(elf[text[4]:text[4] + text[5]])
    rom[0xbd] = (-sum(rom[0xa0:0xbd]) - 0x19) & 0xff
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(rom)
    print(f"Created {len(rom)}-byte diagnostic ROM")
