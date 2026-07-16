import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;


public class HuffmanCompression {

    // ------------------------------------------------------------------
    // Huffman tree node
    // ------------------------------------------------------------------
    private static class HuffmanNode implements Comparable<HuffmanNode> {
        final int byteValue;      // 0-255 for a leaf (actual symbol), -1 for an internal node
        final long frequency;
        final long insertionOrder; // tie-breaker so the heap is fully deterministic
        HuffmanNode left, right;

        HuffmanNode(int byteValue, long frequency, long insertionOrder) {
            this.byteValue = byteValue;
            this.frequency = frequency;
            this.insertionOrder = insertionOrder;
        }

        boolean isLeaf() {
            return left == null && right == null;
        }

        @Override
        public int compareTo(HuffmanNode other) {
            if (this.frequency != other.frequency) {
                return Long.compare(this.frequency, other.frequency);
            }
            // Deterministic tie-break: earlier-inserted node wins, so the same
            // frequency table always builds the exact same tree shape, which
            // is essential since compress() and decompress() rebuild the tree
            // independently from the stored frequency table.
            return Long.compare(this.insertionOrder, other.insertionOrder);
        }
    }

    // ------------------------------------------------------------------
    // Bit-level I/O helpers
    // ------------------------------------------------------------------
    private static class BitWriter {
        private final OutputStream out;
        private int currentByte = 0;
        private int numBits = 0;

        BitWriter(OutputStream out) {
            this.out = out;
        }

        void writeBit(int bit) throws IOException {
            currentByte = (currentByte << 1) | (bit & 1);
            numBits++;
            if (numBits == 8) {
                out.write(currentByte);
                currentByte = 0;
                numBits = 0;
            }
        }

        void writeBits(String bits) throws IOException {
            for (int i = 0; i < bits.length(); i++) {
                writeBit(bits.charAt(i) - '0');
            }
        }

        /** Flushes any partial byte, padding the remainder with zero bits. */
        void flush() throws IOException {
            if (numBits > 0) {
                currentByte <<= (8 - numBits);
                out.write(currentByte);
                numBits = 0;
                currentByte = 0;
            }
        }
    }

    private static class BitReader {
        private final InputStream in;
        private int currentByte;
        private int numBitsRemaining = 0;

        BitReader(InputStream in) {
            this.in = in;
        }

        int readBit() throws IOException {
            if (numBitsRemaining == 0) {
                currentByte = in.read();
                if (currentByte == -1) {
                    throw new EOFException("Unexpected end of compressed stream.");
                }
                numBitsRemaining = 8;
            }
            numBitsRemaining--;
            return (currentByte >> numBitsRemaining) & 1;
        }
    }

    // ------------------------------------------------------------------
    // Frequency table
    // ------------------------------------------------------------------
    private static Map<Integer, Long> buildFrequencyTable(byte[] data) {
        // LinkedHashMap keeps first-seen order, which combined with the
        // insertionOrder tie-breaker above guarantees the tree rebuilt during
        // decompression is identical to the one built during compression.
        Map<Integer, Long> freq = new LinkedHashMap<>();
        for (byte b : data) {
            int key = b & 0xFF;
            freq.merge(key, 1L, Long::sum);
        }
        return freq;
    }

    // ------------------------------------------------------------------
    // Greedy Huffman tree construction using a min-heap (PriorityQueue)
    // ------------------------------------------------------------------
    private static HuffmanNode buildHuffmanTree(Map<Integer, Long> freqTable) {
        PriorityQueue<HuffmanNode> minHeap = new PriorityQueue<>();
        long order = 0;
        for (Map.Entry<Integer, Long> entry : freqTable.entrySet()) {
            minHeap.add(new HuffmanNode(entry.getKey(), entry.getValue(), order++));
        }

        if (minHeap.size() == 1) {
            // Degenerate case: file contains only one distinct byte value.
            return minHeap.poll();
        }

        // Greedy step, repeated until a single tree remains:
        // always pull the two least-frequent nodes and merge them.
        while (minHeap.size() > 1) {
            HuffmanNode left = minHeap.poll();
            HuffmanNode right = minHeap.poll();
            HuffmanNode parent = new HuffmanNode(-1, left.frequency + right.frequency, order++);
            parent.left = left;
            parent.right = right;
            minHeap.add(parent);
        }
        return minHeap.poll();
    }

    // ------------------------------------------------------------------
    // Generate prefix codes by walking the tree (0 = left, 1 = right)
    // ------------------------------------------------------------------
    private static void generateCodes(HuffmanNode node, StringBuilder path, Map<Integer, String> codes) {
        if (node == null) return;
        if (node.isLeaf()) {
            // Single-symbol file edge case: root itself is the only leaf,
            // so the path is empty -- assign it the 1-bit code "0".
            codes.put(node.byteValue, path.length() > 0 ? path.toString() : "0");
            return;
        }
        path.append('0');
        generateCodes(node.left, path, codes);
        path.deleteCharAt(path.length() - 1);

        path.append('1');
        generateCodes(node.right, path, codes);
        path.deleteCharAt(path.length() - 1);
    }

    // ------------------------------------------------------------------
    // Compression
    // ------------------------------------------------------------------
    public static void compress(String inputPath, String outputPath) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(inputPath));
        Map<Integer, Long> freqTable = buildFrequencyTable(data);

        try (DataOutputStream out =
                     new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputPath)))) {

            out.writeInt(freqTable.size());
            for (Map.Entry<Integer, Long> entry : freqTable.entrySet()) {
                out.writeByte(entry.getKey());
                out.writeLong(entry.getValue());
            }
            out.writeLong(data.length);

            if (freqTable.isEmpty()) {
                return; // empty input file -- nothing further to write
            }

            HuffmanNode root = buildHuffmanTree(freqTable);
            Map<Integer, String> codes = new HashMap<>();
            generateCodes(root, new StringBuilder(), codes);

            if (root.isLeaf()) {
                // Only one distinct byte value in the whole file: its count is
                // already in the header, so no bitstream is needed at all.
                return;
            }

            BitWriter bitWriter = new BitWriter(out);
            for (byte b : data) {
                bitWriter.writeBits(codes.get(b & 0xFF));
            }
            bitWriter.flush();
        }

        printCompressionStats(inputPath, outputPath);
    }

    private static void printCompressionStats(String inputPath, String outputPath) {
        long originalSize = new File(inputPath).length();
        long compressedSize = new File(outputPath).length();
        System.out.println("Original size:   " + originalSize + " bytes");
        System.out.println("Compressed size: " + compressedSize + " bytes");
        if (originalSize > 0) {
            double ratio = (1.0 - (double) compressedSize / originalSize) * 100.0;
            System.out.printf("Space saved:      %.2f%%%n", ratio);
        }
    }

    // ------------------------------------------------------------------
    // Decompression
    // ------------------------------------------------------------------
    public static void decompress(String inputPath, String outputPath) throws IOException {
        try (DataInputStream in =
                     new DataInputStream(new BufferedInputStream(new FileInputStream(inputPath)));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputPath))) {

            int tableSize = in.readInt();
            Map<Integer, Long> freqTable = new LinkedHashMap<>();
            for (int i = 0; i < tableSize; i++) {
                int symbol = in.readByte() & 0xFF;
                long frequency = in.readLong();
                freqTable.put(symbol, frequency);
            }
            long totalBytes = in.readLong();

            if (tableSize == 0 || totalBytes == 0) {
                return; // original file was empty
            }

            HuffmanNode root = buildHuffmanTree(freqTable);

            if (root.isLeaf()) {
                // Single distinct byte value repeated totalBytes times.
                byte value = (byte) root.byteValue;
                byte[] chunk = new byte[(int) Math.min(totalBytes, 1 << 16)];
                java.util.Arrays.fill(chunk, value);
                long remaining = totalBytes;
                while (remaining > 0) {
                    int toWrite = (int) Math.min(remaining, chunk.length);
                    out.write(chunk, 0, toWrite);
                    remaining -= toWrite;
                }
                return;
            }

            BitReader bitReader = new BitReader(in);
            HuffmanNode current = root;
            long bytesWritten = 0;
            while (bytesWritten < totalBytes) {
                int bit = bitReader.readBit();
                current = (bit == 0) ? current.left : current.right;
                if (current.isLeaf()) {
                    out.write(current.byteValue);
                    bytesWritten++;
                    current = root;
                }
            }
        }
        System.out.println("Decompressed successfully -> " + outputPath);
    }

    // ------------------------------------------------------------------
    // CLI entry point
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        if (args.length != 3) {
            printUsage();
            return;
        }

        String mode = args[0];
        String inputPath = args[1];
        String outputPath = args[2];

        try {
            long start = System.currentTimeMillis();
            switch (mode.toLowerCase()) {
                case "compress":
                    compress(inputPath, outputPath);
                    break;
                case "decompress":
                    decompress(inputPath, outputPath);
                    break;
                default:
                    printUsage();
                    return;
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Time taken: " + elapsed + " ms");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println("Huffman File Compression Tool");
        System.out.println("Usage:");
        System.out.println("  java HuffmanCompression compress   <inputFile> <outputFile>");
        System.out.println("  java HuffmanCompression decompress <inputFile> <outputFile>");
    }
}
