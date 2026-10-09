"""EmbeddingGemma on the PC, byte-for-byte the same recipe as Llm.embed in the app.

Model files are Gemma-licensed and live in train/model/ (git-ignored); copy them from the phone (see README.md).
"""
import re
import struct
from pathlib import Path

import numpy as np
import sentencepiece as spm
from ai_edge_litert.interpreter import Interpreter

HERE = Path(__file__).parent
MODEL = HERE / "model" / "embeddinggemma-300M_seq256_mixed-precision.tflite"
TOKENIZER = HERE / "model" / "sentencepiece.model"
SEQ = 256
PREFIX = "task: sentence similarity | query: "  # must match Llm.PREFIX


class Embedder:
    def __init__(self):
        self.sp = spm.SentencePieceProcessor(model_file=str(TOKENIZER))
        self.it = Interpreter(model_path=str(MODEL), num_threads=8)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]["index"]
        self.out = self.it.get_output_details()[0]["index"]
        self.cache = {}

    def __call__(self, text: str) -> np.ndarray:
        if text in self.cache:
            return self.cache[text]
        ids = self.sp.encode(PREFIX + text)[: SEQ - 2]
        x = [self.sp.bos_id()] + ids + [self.sp.eos_id()]
        x += [self.sp.pad_id()] * (SEQ - len(x))
        self.it.set_tensor(self.inp, np.array([x], dtype=np.int32))
        self.it.invoke()
        v = self.it.get_tensor(self.out)[0].astype(np.float32)
        v /= np.linalg.norm(v)
        self.cache[text] = v
        return v


# Same split as clauses() in Triage.kt.
_CLAUSE = re.compile(r"[.!?;,]+|\s+(?:at|and|pero|tapos|kasi|but|then|saka)\s+", re.IGNORECASE)


def clauses(text: str) -> list[str]:
    return [c.strip() for c in _CLAUSE.split(text) if len(c.strip()) > 2]


def read_phone_vectors(path: Path) -> dict[str, np.ndarray]:
    """Reads the app's saved example vectors (Llm.writeVectors: Java DataOutputStream)."""
    b = path.read_bytes()
    pos = 0

    def take(n):
        nonlocal pos
        pos += n
        return b[pos - n : pos]

    out = {}
    for _ in range(struct.unpack(">i", take(4))[0]):
        key = take(struct.unpack(">H", take(2))[0]).decode("utf-8")
        dim = struct.unpack(">i", take(4))[0]
        out[key] = np.frombuffer(take(4 * dim), dtype=">f4").astype(np.float32)
    return out


if __name__ == "__main__":
    # Parity gate: PC vectors must match the phone's (cosine >= 0.999) or training on the PC is invalid.
    import time

    phone = read_phone_vectors(HERE / "model" / "phone_vectors.bin")
    e = Embedder()
    t0 = time.time()
    sims = np.array([float(e(k) @ v) for k, v in phone.items()])
    print(f"{len(sims)} vectors, dim {len(next(iter(phone.values())))}, {1000 * (time.time() - t0) / len(sims):.0f} ms each on PC")
    print(f"cosine PC vs phone: min {sims.min():.5f}  mean {sims.mean():.5f}")
    worst = sorted(zip(sims, phone), key=lambda x: x[0])[:3]
    for s, k in worst:
        print(f"  {s:.5f}  {k}")
    print("PARITY OK" if sims.min() >= 0.999 else "PARITY FAILED")
