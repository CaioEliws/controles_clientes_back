"""Remove only audited variable text objects, preserving the supplied PDF drawings."""
from pathlib import Path
import hashlib
from pypdf import PdfReader, PdfWriter
from pypdf.generic import ContentStream, NameObject

source = Path(r"C:\Users\caioe\Downloads\Promissoria emprestimo R$ 400,00 - 01 de outubro de 2026.pdf")
root = Path(__file__).resolve().parents[2] / "src/main/resources/templates"
assert hashlib.sha256(source.read_bytes()).hexdigest() == "1542103a23e954b53c850ad3654989920c90dc5a1634a4a83e4ebe534a7f3841", "Reference changed; repeat layout audit"
original = root / "source/promissoria-original-v1.pdf"
original.write_bytes(source.read_bytes())
reader = PdfReader(original)
assert len(reader.pages) == 1
page = reader.pages[0]
stream = ContentStream(page.get_contents(), reader)
# BT/ET object numbers verified against the original render and content stream.
remove = {4, 5, 9, 10, 11, 32, 33, 35, 36, 37, 40, 41, 43, 44, 45}
out, index, inside = [], -1, False
for args, op in stream.operations:
    if op == b"BT":
        index += 1
        inside = True
    if not (inside and index in remove):
        out.append((args, op))
    if op == b"ET":
        inside = False
assert index == 45, "Reference changed; repeat visual/position audit before preparing"
stream.operations = out
page[NameObject("/Contents")] = stream
writer = PdfWriter()
writer.add_page(page)
writer.add_metadata({"/Title": "Modelo limpo de nota promissoria V1"})
with (root / "promissoria-v1.pdf").open("wb") as result:
    writer.write(result)
print("Source SHA-256:", hashlib.sha256(original.read_bytes()).hexdigest())
