"""Turn the supplied mint1 artwork into the adaptive-icon vector in artwork.py.

    python3 assets/make_mint1_vector.py path/to/mint1.png

mint1 is a full opaque square with three flat colours. Each colour becomes
one mask, and each mask is walked with the same edge tracer that
make_logo_vector.py uses, so the output follows the same conventions: one
path per colour, drawn bottom to top, in the 108-unit adaptive viewport.
"""
import os
import sys

import numpy
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_logo_vector as tracer  # noqa: E402

#: colours sampled from the supplied mint1.png, bottom layer first
PALETTE = [
    ("DARK", (0x38, 0x51, 0x34)),   # the dark blob behind the mark
    ("GREEN", (0x5E, 0xA5, 0x59)),  # the mark and the bottom-right shape
]
BACKGROUND_RGB = (0x18, 0x1D, 0x17)  # the flat ground under both


def masks(path, size=512):
    image = numpy.asarray(Image.open(path).convert("RGB").resize((size, size), Image.LANCZOS),
                          dtype=numpy.float64)
    colours = numpy.array([BACKGROUND_RGB] + [rgb for _, rgb in PALETTE], dtype=numpy.float64)
    distance = ((image[:, :, None, :] - colours[None, None, :, :]) ** 2).sum(-1)
    nearest = distance.argmin(-1)
    return {name: (nearest == i + 1).astype(numpy.float64) for i, (name, _) in enumerate(PALETTE)}


PAD = 2  # shapes run off the square; a blank margin lets every outline close


MIN_AREA = 2.0  # square units in the 108-unit viewport


def shoelace(points):
    total = 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]):
        total += x0 * y1 - x1 * y0
    return abs(total) / 2.0


def trace_mask(alpha):
    scale = tracer.VIEWPORT / alpha.shape[0]
    padded = numpy.pad(alpha, PAD)
    pieces = []
    for loop in sorted(tracer.loops(tracer.edges(padded)), key=len, reverse=True):
        placed = [((x - PAD + 0.5) * scale, (y - PAD + 0.5) * scale) for x, y in loop]
        kept = tracer.fewer(placed, tracer.CLOSE)
        # anti-aliased edge pixels can match a flat colour by accident; a real
        # shape is far larger than a few hundredths of a percent of the square
        if len(kept) >= 3 and shoelace(kept) >= MIN_AREA:
            pieces.append("M" + " L".join("%.2f,%.2f" % p for p in kept) + " Z")
    return " ".join(pieces)


if __name__ == "__main__":
    source = sys.argv[1]
    for name, alpha in masks(source).items():
        data = trace_mask(alpha)
        print("%s %d outlines, %d points" % (name, data.count("M"), data.count(",")), file=sys.stderr)
        print(name + " = " + repr(data))
