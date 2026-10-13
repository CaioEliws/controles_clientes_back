"""Render the official reference and generated PDF with the same renderer/resolution."""
from pathlib import Path
import hashlib
import pypdfium2
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "target/artifacts"
SOURCE = ROOT / "src/main/resources/templates/source/promissoria-original-v1.pdf"

def render(path, page=0):
    with pypdfium2.PdfDocument(str(path)) as pdf:
        return pdf[page].render(scale=3).to_pil().convert("RGB")

OUT.mkdir(parents=True, exist_ok=True)
original = render(SOURCE)
generated = render(OUT / "promissoria-teste.pdf")
assert original.size == generated.size
original.save(OUT / "promissoria-original-render.png")
generated.save(OUT / "promissoria-gerada-render.png")
pair = Image.new("RGB", (original.width * 2, original.height), "white")
pair.paste(original, (0, 0))
pair.paste(generated, (original.width, 0))
pair.save(OUT / "promissoria-comparacao-lado-a-lado.png")
Image.blend(original, generated, .5).save(OUT / "promissoria-overlay-diferencas.png")
pair.crop((0, 70, pair.width, 780)).save(OUT / "promissoria-comparacao-detalhe.png")
mask = Image.new("L", original.size, 255)
draw = ImageDraw.Draw(mask)
# Only variable fields: PDF coordinates in points (origin at bottom left).
for x0, y0, x1, y1 in [(47,600,73,806), (360,794,389,808), (422,794,491,808),
        (509,794,551,808), (405,770,546,790), (158,749,545,765), (129,673,545,696),
        (129,644.5,330,662), (212,623,316,637), (129,592,329,621), (409,652,501,666)]:
    draw.rectangle((int(x0*3),int((842.25-y1)*3),int(x1*3+1),int((842.25-y0)*3+1)),fill=0)
diff = ImageChops.difference(original,generated).convert("L")
static_diff = ImageChops.multiply(diff,mask)
changed = sum(static_diff.histogram()[1:])
print("Different pixels outside variable fields:",changed)
assert changed == 0, "A fixed element differs from the official reference"
with pypdfium2.PdfDocument(str(OUT / "contrato-completo-teste.pdf")) as combined:
    for index in range(len(combined)):
        combined[index].render(scale=1.5).to_pil().save(OUT / f"contrato-completo-pagina-{index + 1}.png")
    last = combined[len(combined)-1].render(scale=3).to_pil().convert("RGB")
    assert ImageChops.difference(generated, last).getbbox() is None, "Merged promissoria differs"
for path in (SOURCE, OUT / "promissoria-teste.pdf", OUT / "contrato-completo-teste.pdf"):
    print(path.name, hashlib.sha256(path.read_bytes()).hexdigest())
print("Resolution:", original.size, "at 216 dpi. Last merged page is pixel-identical.")
