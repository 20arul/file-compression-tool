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


## Build & Run

```bash
javac HuffmanCompression.java

# Compress
java HuffmanCompression compress input.txt output.huff

# Decompress
java HuffmanCompression decompress output.huff restored.txt
```
