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

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Reads one game from a decompressed USAY buffer, the layout EDGE and CycloDS Evolution files share. Strings are
 * UTF-8, end in a zero byte and aren't padded.
 *
 * <pre>
 *   [0:2]  uint16 LE, 0x0004
 *   [2:4]  uint16 LE, top level entry count
 *   [4:8]  uint32 LE, buffer length
 *   [8:]   game title, then "SERIAL CRC32|YYYY/MM/DD HH:MM", then the entries in order
 * </pre>
 *
 * <p>Each entry starts with a type byte, 0x00, a count and 0x00. The type byte's bits, from R4CCE's FUN_0040da4c:
 * <ul>
 *   <li>bit 0 (0x01): a folder</li>
 *   <li>bit 1 (0x02): a code; R4CCE refuses a code without it or a folder with it</li>
 *   <li>bit 2 (0x04): a comment follows the title</li>
 *   <li>bit 3 (0x08): a modifier folder, where one code at a time is on; R4CCE refuses it on a code</li>
 * </ul>
 * Folders seen use 0x01, 0x05, 0x09 and 0x0D; codes 0x02 and 0x06.
 *
 * <p>A code's count is its line count. Its title and comment follow, then each line as two uint32 LE words. A
 * folder's count is its code count, and bytes [4:8] are the folder's whole size, header included, as a uint32 LE.
 * Its title and comment follow, then its codes. Folders don't nest.
 */
class UsayParser {
	private static final int FLAG_HAS_COMMENT = 0x04;

	/**
	 * Reads one game, sharing repeated strings through {@code pool}. Pass the same pool for every game in a file.
	 */
	Game parse(byte[] usay, StringPool pool) {
		ByteBuffer buf = ByteBuffer.wrap(usay).order(ByteOrder.LITTLE_ENDIAN);
		int itemCount = buf.getShort(2) & 0xFFFF;

		buf.position(8);
		String gameTitle = pool.zeroTerminated(buf);

		skipNullTerminated(buf); // serial, CRC32 and date

		var game = new Game();
		game.setTitle(gameTitle);
		game.setEnabled(true); // the format has no game enabled flag
		game.setMasterCode(Collections.emptyList());

		// Folders and codes share one order, so they're written back interleaved as they were
		int topLevelOrder = 0;

		for (int i = 0; i < itemCount && buf.remaining() >= 4; ++i) {
			int type = buf.get(buf.position()) & 0xFF;
			if ((type & 0x01) != 0) {
				Folder folder = parseFolder(buf, pool);
				if (folder != null) {
					folder.setSortOrder(topLevelOrder++);
					game.getFolders().add(folder);
				}
			} else {
				Code code = parseCode(buf, pool);
				if (code != null) {
					code.setSortOrder(topLevelOrder++);
					game.getCodes().add(code);
				}
			}
		}

		return game;
	}

	/**
	 * Moves past the bytes up to a zero byte, and past the zero byte.
	 */
	private static void skipNullTerminated(ByteBuffer buf) {
		while (buf.hasRemaining() && buf.get(buf.position()) != 0) {
			buf.get();
		}

		buf.get(); // the zero byte
	}

	private Folder parseFolder(ByteBuffer buf, StringPool pool) {
		if (buf.remaining() < 8) {
			return null;
		}

		int folderStart = buf.position();
		int typeByte = buf.get() & 0xFF;
		buf.get(); // byte[1] = 0x00
		int codeCount = buf.get() & 0xFF;
		buf.get(); // byte[3] = 0x00
		int contentSize = buf.getInt();
		int folderEnd = folderStart + contentSize;

		String title = pool.zeroTerminated(buf);

		var folder = new Folder();
		folder.setTitle(title);
		folder.setModifier((typeByte & 0x08) != 0);

		if ((typeByte & 0x04) != 0) {
			folder.setComment(pool.text(StringPool.decodeZeroTerminated(buf).trim()));
		}

		for (int i = 0; i < codeCount && buf.position() < folderEnd; ++i) {
			Code code = parseCode(buf, pool);
			if (code != null) {
				folder.getCodes().add(code);
			}
		}

		// Continue from the folder's stated end
		if (folderEnd > 0 && folderEnd <= buf.limit()) {
			buf.position(folderEnd);
		}

		return folder;
	}

	private Code parseCode(ByteBuffer buf, StringPool pool) {
		if (buf.remaining() < 4) {
			return null;
		}

		int flags = buf.get() & 0xFF;
		buf.get(); // byte[1] = 0x00
		int lineCount = buf.get() & 0xFF;
		buf.get(); // byte[3] = 0x00

		String title = pool.zeroTerminated(buf);
		String comment = ((flags & FLAG_HAS_COMMENT) != 0) ? pool.zeroTerminated(buf) : "";

		var lines = new ArrayList<String>(lineCount);

		for (int i = 0; i < lineCount && buf.remaining() >= 8; ++i) {
			lines.add(pool.line(buf));
		}

		var code = new Code();
		code.setTitle(title);
		code.setComment(comment);
		code.setCodeLines(lines);
		code.setEnabled(false); // the flags are stored apart, and AbstractCycloDsParser applies them
		return code;
	}
}
