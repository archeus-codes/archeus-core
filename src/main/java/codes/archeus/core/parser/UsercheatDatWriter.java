/*
 * Copyright 2021-2026 Deiondre Andrews (Demonic722)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package codes.archeus.core.parser;

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import codes.archeus.core.util.CheatFileConstants;

import java.util.List;

/**
 * Serializes a {@link CheatFile} and its games back to the binary {@code usrcheat.dat} format.
 * The inverse of {@link UsercheatDatParser}.
 */
public class UsercheatDatWriter implements CheatFileWriter {
	static final int HEADER_SIZE = 256;
	static final int INDEX_ENTRY_SIZE = 16;
	private static final int MASTER_CODE_SIZE = CheatFileConstants.MASTER_CODE_WORDS * 4;
	/** Bit 24 of a code's header word. */
	private static final int CODE_ENABLED = 0x01000000;

	/** The file's first 16 bytes. R4CCE refuses a file without them. */
	static final byte[] FILE_MAGIC = {
		'R', '4', ' ', 'C', 'h', 'e', 'a', 't', 'C', 'o', 'd', 'e', 0x00, 0x01, 0x00, 0x00
	};

	/** A tag R4CCE writes at 0x4C, after the 60-byte title, likely its signature ("YASU", its author). */
	private static final byte[] HEADER_MAGIC = {
		(byte) 0xD5, 0x53, 0x41, 0x59
	};

	/** The start of an EZ-Flash {@code ezarcode.dat}, ending in a zero byte. */
	static final byte[] EZ_MAGIC = "EZ ARDS CheatCode Data Base\0".getBytes(StandardCharsets.US_ASCII);

	/** Where an {@code ezarcode.dat} holds its title, and the most bytes of it that leave a zero byte before 0x4C. */
	static final int EZ_TITLE_OFFSET = 0x20;
	static final int EZ_TITLE_BYTES = 43;

	/**
	 * The files this writer lays out: R4CCE's {@code usrcheat.dat}, or EZ-Flash's {@code ezarcode.dat} before
	 * {@link EzArCodeWriter} XORs it. The EZ layout has its own header, no master codes, and 0 in each code header's
	 * size field.
	 */
	enum Layout {
		USRCHEAT,
		EZARCODE
	}

	private final Layout layout;

	public UsercheatDatWriter() {
		this(Layout.USRCHEAT);
	}

	UsercheatDatWriter(Layout layout) {
		this.layout = layout;
	}

	/**
	 * Whether every line of a code can be written. The format stores each line as two 32-bit numbers, so a line with a
	 * placeholder for the user to fill in, such as {@code 0000????}, can't be.
	 */
	public static boolean canWrite(List<String> codeLines) {
		for (String line : codeLines) {
			if (!isWritableLine(line)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void write(CheatFile cheatFile, List<Game> games, OutputStream out) throws IOException {
		// Sizes first, so the index can hold each game's offset
		int[] gameDataSizes = new int[games.size()];

		for (int i = 0; i < games.size(); ++i) {
			gameDataSizes[i] = computeGameDataSize(games.get(i));
		}

		int indexSize = (games.size() + 1) * INDEX_ENTRY_SIZE; // games + sentinel
		int firstGameOffset = HEADER_SIZE + indexSize;

		// Each game gets its own buffer, so the whole file is never in memory at once
		ByteBuffer head = ByteBuffer.allocate(firstGameOffset).order(ByteOrder.LITTLE_ENDIAN);

		writeHeader(head, cheatFile);
		writeGameIndex(head, games, gameDataSizes, firstGameOffset);
		out.write(head.array());

		for (int i = 0; i < games.size(); ++i) {
			ByteBuffer gameData = ByteBuffer.allocate(gameDataSizes[i]).order(ByteOrder.LITTLE_ENDIAN);
			writeGameData(gameData, games.get(i));
			out.write(gameData.array());
		}
	}

	private void writeHeader(ByteBuffer buf, CheatFile cheatFile) {
		if (layout == Layout.EZARCODE) {
			writeEzHeader(buf, cheatFile);
			return;
		}

		int start = buf.position();

		buf.put(FILE_MAGIC);

		byte[] titleBytes = cheatFile.getTitle().getBytes(StandardCharsets.UTF_8);
		int titleWriteLen = Math.min(titleBytes.length, 60);
		buf.put(titleBytes, 0, titleWriteLen);

		buf.position(start + 0x4C);
		buf.put(HEADER_MAGIC);

		buf.position(start + 0x50);
		buf.put(cheatFile.isEnabled() ? (byte) 0x01 : (byte) 0x00);

		buf.position(start + HEADER_SIZE);
	}

	/**
	 * The EZ header: its magic, the title cut to whole characters, and zeros for the rest, the enabled flag included.
	 */
	private void writeEzHeader(ByteBuffer buf, CheatFile cheatFile) {
		int start = buf.position();
		buf.put(EZ_MAGIC);

		String title = cheatFile.getTitle() == null ? "" : cheatFile.getTitle();
		buf.position(start + EZ_TITLE_OFFSET);
		buf.put(title.substring(0, Utf8Text.prefixWithin(title, EZ_TITLE_BYTES)).getBytes(StandardCharsets.UTF_8));

		buf.position(start + HEADER_SIZE);
	}

	private void writeGameIndex(ByteBuffer buf, List<Game> games, int[] gameDataSizes, int firstGameOffset) {
		int offset = firstGameOffset;

		for (int i = 0; i < games.size(); ++i) {
			Game game = games.get(i);

			byte[] serialBytes = new byte[4];
			byte[] serial = game.getSerial().getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(serial, 0, serialBytes, 0, Math.min(serial.length, 4));
			buf.put(serialBytes);

			buf.putInt((int) Long.parseLong(game.getCrc32(), 16));
			buf.putInt(offset);
			buf.putInt(0); // Reserved

			offset += gameDataSizes[i];
		}
		// Sentinel: 16 zero bytes
		buf.put(new byte[INDEX_ENTRY_SIZE]);
	}

	private void writeGameData(ByteBuffer buf, Game game) {
		writeGameTitle(buf, game.getTitle());

		int totalEntries = game.getCodes().size()
				+ game.getFolders().stream().mapToInt(f -> 1 + f.getCodes().size()).sum();

		buf.put((byte) (totalEntries & 0xFF));
		buf.put((byte) ((totalEntries >> 8) & 0xFF));
		buf.put((byte) ((totalEntries >> 16) & 0xFF));
		buf.put(game.isEnabled() ? (byte) 0xF0 : (byte) 0x00);

		writeMasterCode(buf, game.getMasterCode());

		for (Object entry : game.getTopLevelEntriesInOrder()) {
			if (entry instanceof Code code) {
				writeCodeEntry(buf, code);
			} else if (entry instanceof Folder folder) {
				writeFolderEntry(buf, folder);
			}
		}
	}

	/**
	 * Writes the title padded with 1 to 4 zero bytes to a multiple of 4, so its last chunk ends in a zero.
	 */
	private void writeGameTitle(ByteBuffer buf, String title) {
		byte[] titleBytes = title.getBytes(StandardCharsets.UTF_8);
		buf.put(titleBytes);
		int padding = 4 - (titleBytes.length % 4); // 1, 2, 3, or 4
		buf.put(new byte[padding]);
	}

	private void writeMasterCode(ByteBuffer buf, List<String> masterCode) {
		if (layout == Layout.EZARCODE) {
			buf.put(new byte[MASTER_CODE_SIZE]);
			return;
		}

		List<String> codes = masterCode.isEmpty() ? CheatFileConstants.NULL_MASTER_CODE : masterCode;

		for (int i = 0; i < CheatFileConstants.MASTER_CODE_WORDS; ++i) {
			buf.putInt(i < codes.size() ? (int) Long.parseLong(codes.get(i), 16) : 0);
		}
	}

	private void writeFolderEntry(ByteBuffer buf, Folder folder) {
		int codeCount = folder.getCodes().size();
		buf.put((byte) (codeCount & 0xFF));
		buf.put((byte) ((codeCount >> 8) & 0xFF));
		buf.put((byte) 0x00);
		buf.put(folder.isModifier() ? (byte) 0x11 : (byte) 0x10);

		writeTitleComment(buf, folder.getTitle(), folder.getComment());

		for (Code code : folder.getCodes()) {
			writeCodeEntry(buf, code);
		}
	}

	/**
	 * Writes a code: a header word, its title and comment, its length in words, then its lines. The header holds the
	 * entry's size in words after the header (bits 0 to 23) and whether it's enabled (bit 24). Readers such as
	 * DeSmuME step from entry to entry by that size.
	 */
	private void writeCodeEntry(ByteBuffer buf, Code code) {
		int header = buf.position();
		buf.putInt(0);

		writeTitleComment(buf, code.getTitle(), code.getComment());

		List<String> codeLines = code.getCodeLines();
		int codeLength = codeLines.size() * 2;
		buf.put((byte) (codeLength & 0xFF));
		buf.put((byte) ((codeLength >> 8) & 0xFF));
		buf.put((byte) 0x00);
		buf.put((byte) 0x00);

		for (String line : codeLines) {
			String[] parts = line.split(" ", 2);
			buf.putInt((int) Long.parseLong(parts[0], 16));
			buf.putInt((int) Long.parseLong(parts[1], 16));
		}

		int sizeInWords = layout == Layout.EZARCODE ? 0 : (buf.position() - header) / 4 - 1;
		buf.putInt(header, sizeInWords | (code.isEnabled() ? CODE_ENABLED : 0));
	}

	/**
	 * Writes a title and optional comment in 4-byte chunks, as R4CCE does. The title ends at a zero byte, and the
	 * comment starts in the free bytes of that chunk. A reader takes that chunk's bytes 2 and 3 both being zero to
	 * mean no comment, so the comment's first bytes are right aligned there and byte 3 is never zero. The comment ends
	 * at a zero byte, or with an all zero chunk when it fills its last chunk.
	 */
	private void writeTitleComment(ByteBuffer buf, String title, String comment) {
		byte[] titleBytes = title.getBytes(StandardCharsets.UTF_8);
		byte[] commentBytes = (comment != null && !comment.isEmpty())
				? comment.getBytes(StandardCharsets.UTF_8)
				: new byte[0];
		boolean hasComment = commentBytes.length > 0;

		int nullPos = titleBytes.length % 4; // 0, 1, 2, or 3
		int fullChunks = titleBytes.length / 4;

		buf.put(titleBytes, 0, fullChunks * 4);

		// The title's last bytes, its zero and room for the comment's first bytes
		byte[] termChunk = new byte[4];
		System.arraycopy(titleBytes, fullChunks * 4, termChunk, 0, nullPos);

		if (!hasComment) {
			buf.put(termChunk);
			// Byte 2 is the title's, so the reader expects a comment and needs an all zero chunk to end it
			if (nullPos == 3) {
				buf.put(new byte[4]);
			}
		} else {
			if (nullPos < 3) {
				int available = 3 - nullPos;
				int packCount = Math.min(available, commentBytes.length);
				int packStart = 4 - packCount;
				System.arraycopy(commentBytes, 0, termChunk, packStart, packCount);
				buf.put(termChunk);
				writeCommentChunks(buf, commentBytes, packCount);
			} else {
				buf.put(termChunk);
				writeCommentChunks(buf, commentBytes, 0);
			}
		}
	}

	/**
	 * Writes comment bytes starting at {@code startOffset} in 4-byte chunks, terminated
	 * by a null byte in the last chunk or an explicit all zero chunk.
	 */
	private void writeCommentChunks(ByteBuffer buf, byte[] commentBytes, int startOffset) {
		int remaining = commentBytes.length - startOffset;
		
		if (remaining == 0) {
			buf.put(new byte[4]); // all zero terminates the comment loop
			return;
		}
		
		int written = 0;
		
		while (written < remaining) {
			int chunkBytes = Math.min(4, remaining - written);
			buf.put(commentBytes, startOffset + written, chunkBytes);
			if (chunkBytes < 4) {
				buf.put(new byte[4 - chunkBytes]); // null padding terminates the loop
			}
			written += chunkBytes;
		}
		
		if (remaining % 4 == 0) {
			buf.put(new byte[4]); // last chunk was full (need explicit all zero terminator)
		}
	}

	private int computeCommentSectionSize(int remainingComment) {
		if (remainingComment == 0) {
			return 4;
		} else if (remainingComment % 4 == 0) {
			return remainingComment + 4;
		} else {
			return (remainingComment / 4 + 1) * 4;
		}
	}

	private int computeGameDataSize(Game game) {
		int titleSize = computeGameTitleSize(game.getTitle());
		int headerSize = 4;
		int codesSize = game.getCodes().stream().mapToInt(this::computeCodeSize).sum();
		int foldersSize = game.getFolders().stream().mapToInt(this::computeFolderSize).sum();
		return titleSize + headerSize + MASTER_CODE_SIZE + codesSize + foldersSize;
	}

	private int computeGameTitleSize(String title) {
		byte[] titleBytes = title.getBytes(StandardCharsets.UTF_8);
		return (titleBytes.length / 4 + 1) * 4;
	}

	private int computeFolderSize(Folder folder) {
		int headerSize = 4;
		int titleCommentSize = computeTitleCommentSize(folder.getTitle(), folder.getComment());
		int codesSize = folder.getCodes().stream().mapToInt(this::computeCodeSize).sum();
		return headerSize + titleCommentSize + codesSize;
	}

	private int computeCodeSize(Code code) {
		int headerSize = 4;
		int titleCommentSize = computeTitleCommentSize(code.getTitle(), code.getComment());
		int codeLengthHeaderSize = 4;
		int codeDataSize = code.getCodeLines().size() * 8; // 2 x 4 bytes per line
		return headerSize + titleCommentSize + codeLengthHeaderSize + codeDataSize;
	}

	/**
	 * The number of bytes {@link #writeTitleComment} writes for a title and comment.
	 */
	private int computeTitleCommentSize(String title, String comment) {
		byte[] titleBytes = title.getBytes(StandardCharsets.UTF_8);
		byte[] commentBytes = (comment != null && !comment.isEmpty())
				? comment.getBytes(StandardCharsets.UTF_8)
				: new byte[0];

		int nullPos = titleBytes.length % 4;
		int titleSectionSize = (titleBytes.length / 4 + 1) * 4;

		if (commentBytes.length == 0) {
			int extraChunk = (nullPos == 3) ? 4 : 0;
			return titleSectionSize + extraChunk;
		}

		int packedInTitle = (nullPos < 3) ? Math.min(3 - nullPos, commentBytes.length) : 0;
		int remainingComment = commentBytes.length - packedInTitle;

		return titleSectionSize + computeCommentSectionSize(remainingComment);
	}

	/**
	 * Whether the line is two groups of 8 hex digits with one space between, such as {@code 02000000 0000FFFF}. Checked
	 * by hand rather than with a pattern, since it runs on every line of a full database.
	 */
	private static boolean isWritableLine(String line) {
		if (line.length() != 17 || line.charAt(8) != ' ') {
			return false;
		}

		for (int i = 0; i < 17; ++i) {
			if (i != 8 && !isHexDigit(line.charAt(i))) {
				return false;
			}
		}

		return true;
	}

	private static boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
	}
}
