# Huffman File Compression Tool (Java)

A command-line lossless file compressor/decompressor built entirely on
**Huffman Coding**, demonstrating three core CS concepts in one working system:

| Concept | Where it's used |
|---|---|
| **Binary Tree** | The Huffman tree — leaves are byte values, internal nodes are merged frequencies |
| **Priority Queue (Min-Heap)** | `java.util.PriorityQueue` always gives us the two lowest-frequency nodes to merge next |
| **Greedy Algorithm** | Repeatedly merging the two least-frequent nodes is a greedy strategy, provably optimal for prefix-free codes |

Works on **any file type** (text, images, binaries) since it operates on raw bytes (0–255), not just characters.

## How it works

1. **Frequency analysis** — scan the file once and count occurrences of each byte value (0–255).
2. **Build the Huffman tree (greedy + heap)** — put every byte value into a min-heap keyed by frequency. Repeatedly pop the two smallest nodes, merge them into a new internal node (frequency = sum), and push it back. This is the classic greedy algorithm; because you always combine the two rarest symbols first, the resulting tree is provably optimal (minimizes total encoded length).
3. **Generate prefix codes** — walk the tree root to leaf; each left branch appends `0`, each right branch appends `1`. Because every symbol lives at a leaf, no code is a prefix of another (a "prefix-free" code), which is what lets the decoder read the bitstream unambiguously with no delimiters.
4. **Encode** — replace every byte in the file with its bit-string code and pack the bits tightly into bytes.
5. **Decode** — rebuild the identical tree from the stored frequency table, then walk it bit-by-bit from the root; each time a leaf is hit, emit that byte and restart at the root.

## Compressed file format

```
[int]  numUniqueBytes
repeated numUniqueBytes times:
    [byte] symbol (0-255)
    [long] frequency
[long] totalOriginalBytes
[bit-packed Huffman-encoded payload]
```

Storing the exact original byte count means the decoder always knows exactly
when to stop — the last, partially-filled byte of the bitstream can be
zero-padded with no ambiguity and no extra "padding length" field needed.

**Edge cases handled explicitly:**
- Empty input file → header only, no payload.
- File with only one distinct byte value → no tree traversal needed at decode time; the byte is just repeated `totalOriginalBytes` times.
- Deterministic tree reconstruction — nodes carry an `insertionOrder` tie-breaker so the heap resolves equal-frequency ties identically on both the compress and decompress sides.

## Build & Run

```bash
javac HuffmanCompression.java

# Compress
java HuffmanCompression compress input.txt output.huff

# Decompress
java HuffmanCompression decompress output.huff restored.txt
```



## Complexity

Let `n` = file size in bytes, `k` = number of distinct byte values (≤ 256).

| Step | Time | Notes |
|---|---|---|
| Frequency count | O(n) | single pass |
| Build heap | O(k log k) | k ≤ 256, effectively constant |
| Build Huffman tree | O(k log k) | k−1 merge operations, each O(log k) |
| Generate codes | O(k) | tree has ≤ 2k−1 nodes |
| Encode | O(n) | one lookup + bit-write per byte |
| Decode | O(n) | one tree walk per output byte |

**Overall: O(n)** — dominated by the linear scans over file data, since `k` is bounded by 256 regardless of file size. Space is O(n) for the in-memory byte buffer plus O(k) for the tree/code table.

## Why greedy is optimal here

At each step the algorithm merges the two globally least-frequent remaining nodes. An exchange argument shows that in any optimal prefix code, the two rarest symbols must be siblings at the deepest level of the tree — swapping them there only ever costs the same or less. Since merging preserves this property inductively at every step, the greedy choice never forecloses an optimal solution, which is exactly what makes Huffman coding a textbook example of the **greedy-choice property** combined with **optimal substructure**.

## Notes on compression ratio

- Highly structured/repetitive data (English text, source code, logs) compresses well (~40–60% typical here).
- Already-random or high-entropy data (encrypted files, random binary, already-compressed formats like JPEG/ZIP) will **not** shrink — Huffman coding can only exploit skewed frequency distributions, and may even slightly expand such files due to the header overhead. This is expected and matches information-theoretic limits (entropy).
- Very small files can also "expand" because the frequency-table header has fixed overhead that isn't amortized over enough data.

## Possible extensions

- Adaptive/dynamic Huffman coding (single pass, no header needed).
- Canonical Huffman codes to shrink the header further.
- Chunked/streaming processing for very large files instead of loading the whole file into memory.
