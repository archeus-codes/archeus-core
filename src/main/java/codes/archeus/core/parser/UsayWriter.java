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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes a game as a decompressed USAY buffer, laid out as {@link UsayParser} describes.
 */
class UsayWriter {
	/** The date R4CCE writes for a game whose source file has none, as {@code usrcheat.dat} never does. */
	private static final String NO_DATE = "0000/00/00 00:00";

	byte[] write(Game game) {
		ByteArrayOutputStream body = new ByteArrayOutputStream();

		writeNullTerminated(body, game.getTitle());

		String meta = game.getSerial() + " " + game.getCrc32() + "|" + NO_DATE;
		writeNullTerminated(body, meta);

		for (Object entry : game.getTopLevelEntriesInOrder()) {
			if (entry instanceof Folder folder) {
				writeFolder(body, folder);
			} else if (entry instanceof Code code) {
				writeCode(body, code);
			}
		}

		byte[] bodyBytes = body.toByteArray();
		int itemCount = game.getFolders().size() + game.getCodes().size();
		int totalLength = 8 + bodyBytes.length;

		ByteBuffer buf = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) 0x0004);
		buf.putShort((short) itemCount);
		buf.putInt(totalLength);
		buf.put(bodyBytes);

		return buf.array();
	}

	private void writeFolder(ByteArrayOutputStream out, Folder folder) {
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		writeNullTerminated(content, folder.getTitle());

		boolean hasComment = folder.getComment() != null && !folder.getComment().isEmpty();

		if (hasComment) {
			writeNullTerminated(content, folder.getComment());
		}

		for (Code code : folder.getCodes()) {
			writeCode(content, code);
		}

		byte[] contentBytes = content.toByteArray();
		int contentSize = 8 + contentBytes.length;

		int typeByte = 0x01;

		if (hasComment) {
			typeByte |= 0x04;
		}

		if (folder.isModifier()) {
			typeByte |= 0x08;
		}

		out.write(typeByte);
		out.write(0x00); // reserved
		out.write(count(folder.getCodes().size(), UsayLimits.MAX_FOLDER_CODES, folder.getTitle()));
		out.write(0x00); // reserved

		out.write(contentSize & 0xFF);
		out.write((contentSize >> 8) & 0xFF);
		out.write((contentSize >> 16) & 0xFF);
		out.write((contentSize >> 24) & 0xFF);
		out.write(contentBytes, 0, contentBytes.length);
	}

	private void writeCode(ByteArrayOutputStream out, Code code) {
		boolean hasComment = code.getComment() != null && !code.getComment().isEmpty();
		int flags = hasComment ? 0x06 : 0x02;
		List<String> lines = code.getCodeLines();

		out.write(flags);
		out.write(0x00);
		out.write(count(lines.size(), UsayLimits.MAX_CODE_LINES, code.getTitle()));
		out.write(0x00);

		writeNullTerminated(out, code.getTitle());

		if (hasComment) {
			writeNullTerminated(out, code.getComment());
		}

		for (String line : lines) {
			String[] parts = line.split(" ", 2);
			long addr = Long.parseLong(parts[0], 16);
			long val  = Long.parseLong(parts[1], 16);

			out.write((int) (addr & 0xFF));
			out.write((int) ((addr >> 8) & 0xFF));
			out.write((int) ((addr >> 16) & 0xFF));
			out.write((int) ((addr >> 24) & 0xFF));
			out.write((int) (val & 0xFF));
			out.write((int) ((val >> 8) & 0xFF));
			out.write((int) ((val >> 16) & 0xFF));
			out.write((int) ((val >> 24) & 0xFF));
		}
	}

	/**
	 * A count the format stores in one byte. {@link UsayLimits#apply(List)} keeps them in range; this refuses one
	 * that isn't, rather than writing it wrapped around.
	 */
	private static int count(int value, int max, String title) {
		if (value > max) {
			throw new IllegalArgumentException("'" + title + "' has " + value + " entries; a USAY record holds " + max);
		}

		return value;
	}

	private void writeNullTerminated(ByteArrayOutputStream out, String s) {
		if (s != null && !s.isEmpty()) {
			byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
			out.write(bytes, 0, bytes.length);
		}

		out.write(0);
	}
}
