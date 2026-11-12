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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

/**
 * Writes one game's codes as a melonDS cheat file ({@code .mch}), which melonDS 1.1 and later load from beside the ROM
 * when the file is named after it. The format, from melonDS's {@code ARCodeFile.cpp}:
 * <pre>
 * ROOT
 *
 * CODE 1 Moon Jump (Hold L)
 * DESC Hold L to float upward
 * 94000130 FDFF0000
 * 120A1B2C 0000F000
 * D2000000 00000000
 *
 * CAT 1 Weather Mod Codes
 * CODE 0 Sunny
 * 22041A30 00000001
 * </pre>
 * {@code CAT <0|1>} starts a folder (1: only one of its codes may be on), {@code ROOT} the top level codes, and
 * {@code CODE <0|1>} a code that starts off or on. {@code DESC} describes the line before it; {@code #} lines are
 * ignored.
 *
 * <p>The file is one melonDS loads unchanged: names and descriptions fit its limits ({@link #name},
 * {@link #describe}), only the first code in a one at a time folder is on, and codes with placeholder lines, which it
 * can't read, are left out.
 */
public class MelonDsWriter {
	public static final String EXTENSION = ".mch";
	public static final String UNTITLED = "(untitled)";

	static final int MAX_NAME_BYTES = 127;
	static final int MAX_DESCRIPTION_BYTES = 255;

	/** Well under the 1,023 bytes melonDS reads as one line; a longer line fails the whole file. */
	private static final int MAX_NOTE_BYTES = 1000;
	private static final String CUT_MARK = "…";
	private static final String CREDIT_PREFIX = "By ";
	private static final String CREDIT_SEPARATOR = " | ";
	private static final Pattern LINE_BREAKS = Pattern.compile("\\s*[\\r\\n]+\\s*");

	/**
	 * The file name for a game, such as {@code Animal Crossing - Wild World (USA) [ADME 8F7851CB].mch}
	 * ({@link GameFileNames#of}).
	 */
	public static String fileName(Game game) {
		return GameFileNames.of(game, EXTENSION);
	}

	public void write(Game game, OutputStream out) throws IOException {
		Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
		boolean afterFolder = true;

		for (Object entry : game.getTopLevelEntriesInOrder()) {
			if (entry instanceof Folder folder) {
				writeFolder(writer, folder);
				afterFolder = true;
			} else if (entry instanceof Code code && writable(code)) {
				if (afterFolder) {
					writer.write("ROOT\n\n");
					afterFolder = false;
				}
				writeCode(writer, code, code.isEnabled());
			}
		}

		writer.flush();
	}

	/**
	 * What a comment becomes: the {@code DESC} text and the {@code #} lines above the entry. A comment over the limit
	 * moves its credit ({@code "By X | "}) to a note; if it still doesn't fit, the description is cut at a word and the
	 * full comment goes in the notes too.
	 */
	public static Description describe(String comment) {
		String text = oneLine(comment);

		if (text.isEmpty()) {
			return new Description(null, List.of());
		}

		if (bytes(text) <= MAX_DESCRIPTION_BYTES) {
			return new Description(text, List.of());
		}

		String credit = null;
		String rest = text;

		if (text.startsWith(CREDIT_PREFIX)) {
			int separator = text.indexOf(CREDIT_SEPARATOR);
			credit = separator < 0 ? text : text.substring(0, separator);
			rest = separator < 0 ? "" : text.substring(separator + CREDIT_SEPARATOR.length());
		}

		if (rest.isEmpty()) {
			return new Description(cut(text, MAX_DESCRIPTION_BYTES), List.of(text));
		}

		List<String> notes = new ArrayList<>();

		if (credit != null) {
			notes.add(credit);
		}

		if (bytes(rest) <= MAX_DESCRIPTION_BYTES) {
			return new Description(rest, notes);
		}

		notes.add(rest);
		return new Description(cut(rest, MAX_DESCRIPTION_BYTES), notes);
	}

	/**
	 * A name as melonDS keeps it: on one line and at most {@value #MAX_NAME_BYTES} bytes, past which melonDS cuts it.
	 * A blank one becomes {@value #UNTITLED}, since melonDS refuses the whole file over a nameless {@code CODE} or
	 * {@code CAT} line.
	 */
	public static String name(String title) {
		String name = oneLine(title);
		return name.isEmpty() ? UNTITLED : cut(name, MAX_NAME_BYTES);
	}

	/**
	 * Writes the folder and its codes. In a one at a time folder only the first enabled code is written as on, since
	 * melonDS switches off the rest when it loads the file.
	 */
	private static void writeFolder(Writer writer, Folder folder) throws IOException {
		Description description = describe(folder.getComment());
		writeNotes(writer, description.notes());
		writer.write("CAT " + (folder.isModifier() ? 1 : 0) + " " + name(folder.getTitle()) + "\n");
		writeDescription(writer, description);
		writer.write("\n");

		boolean oneOn = false;

		for (Code code : folder.getCodes()) {
			if (writable(code)) {
				boolean enabled = code.isEnabled() && !(folder.isModifier() && oneOn);
				oneOn |= enabled;
				writeCode(writer, code, enabled);
			}
		}
	}

	private static void writeCode(Writer writer, Code code, boolean enabled) throws IOException {
		Description description = describe(code.getComment());
		writeNotes(writer, description.notes());
		writer.write("CODE " + (enabled ? 1 : 0) + " " + name(code.getTitle()) + "\n");
		writeDescription(writer, description);

		for (String line : code.getCodeLines()) {
			writer.write(line.toUpperCase() + "\n");
		}

		writer.write("\n");
	}

	private static void writeDescription(Writer writer, Description description) throws IOException {
		if (description.text() != null) {
			writer.write("DESC " + description.text() + "\n");
		}
	}

	private static void writeNotes(Writer writer, List<String> notes) throws IOException {
		for (String note : notes) {
			for (String line : wrap(note)) {
				writer.write("# " + line + "\n");
			}
		}
	}

	/**
	 * False for codes with placeholder lines such as {@code 0000????}: melonDS fails the whole file on them.
	 */
	private static boolean writable(Code code) {
		return UsercheatDatWriter.canWrite(code.getCodeLines());
	}

	private static String oneLine(String text) {
		return text == null ? "" : LINE_BREAKS.matcher(text.strip()).replaceAll(" ");
	}

	/**
	 * The text if it fits in {@code maxBytes} of UTF-8, otherwise as much as fits with the cut mark, ending at a space
	 * when one falls in the second half.
	 */
	private static String cut(String text, int maxBytes) {
		if (bytes(text) <= maxBytes) {
			return text;
		}

		int end = prefixWithin(text, maxBytes - bytes(CUT_MARK));
		int space = text.lastIndexOf(' ', end);

		if (space > end / 2) {
			end = space;
		}

		return text.substring(0, end).stripTrailing() + CUT_MARK;
	}

	/**
	 * The text in lines of at most {@value #MAX_NOTE_BYTES} bytes, broken at spaces where possible.
	 */
	private static List<String> wrap(String text) {
		List<String> lines = new ArrayList<>();
		String rest = text;

		while (bytes(rest) > MAX_NOTE_BYTES) {
			int end = prefixWithin(rest, MAX_NOTE_BYTES);
			int space = rest.lastIndexOf(' ', end);

			if (space > 0) {
				end = space;
			}

			lines.add(rest.substring(0, end).stripTrailing());
			rest = rest.substring(end).stripLeading();
		}

		lines.add(rest);
		return lines;
	}

	/**
	 * The length of the longest prefix of whole characters that fits in {@code maxBytes} of UTF-8.
	 */
	private static int prefixWithin(String text, int maxBytes) {
		int used = 0;
		int index = 0;

		while (index < text.length()) {
			int codePoint = text.codePointAt(index);
			int size = bytes(Character.toString(codePoint));

			if (used + size > maxBytes) {
				break;
			}

			used += size;
			index += Character.charCount(codePoint);
		}

		return index;
	}

	private static int bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

	/**
	 * @param text  the {@code DESC} text, or null when there's none
	 * @param notes the {@code #} lines above the entry, each before wrapping
	 */
	public record Description(String text, List<String> notes) {
	}
}
