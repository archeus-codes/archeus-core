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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

/**
 * Writes one game as a DeSmuME cheat file ({@code .dct}), in the layout DeSmuME's own {@code CHEATS::save} writes:
 * <pre>
 * ; DeSmuME cheats file. VERSION 2.000
 * Serial=NTR-AJLE-USA
 *
 * ; cheats list
 * AR 1 52007DECAA000018,02007DEC11C100B0,D200000000000000 ;Infinite Health
 * AR 0 1234567800000001 ;Weather Mod Codes [Choose one.]: Sunny | By Datel
 * </pre>
 * DeSmuME has no folders, so a folder code's description names its folder ({@link #description}). DeSmuME
 * ignores {@code Name=}, so it's left out, as are codes DeSmuME can't run ({@link #writable}).
 */
public class DesmumeWriter {
	public static final String EXTENSION = ".dct";

	/** The most lines DeSmuME reads in a code. */
	public static final int MAX_LINES = 1024;
	/** DeSmuME cuts a longer description. */
	static final int MAX_DESCRIPTION_BYTES = 1023;

	private static final String NEWLINE = "\r\n";
	/** The game's region, from the last letter of its serial. */
	private static final Map<Character, String> REGIONS = Map.ofEntries(
			Map.entry('J', "JPN"),
			Map.entry('P', "EUR"),
			Map.entry('F', "FRA"),
			Map.entry('S', "ESP"),
			Map.entry('E', "USA"),
			Map.entry('O', "INT"),
			Map.entry('D', "NOE"),
			Map.entry('I', "ITA"),
			Map.entry('R', "RUS"),
			Map.entry('K', "KOR"),
			Map.entry('H', "HOL"),
			Map.entry('X', "EUU"),
			Map.entry('V', "EUU"),
			Map.entry('W', "EUU"),
			Map.entry('U', "AUS"),
			Map.entry('C', "CHN")
	);
	private static final String UNKNOWN_REGION = "???";

	/**
	 * The file name for a game ({@link GameFileNames#of}), ending in {@code .dct}.
	 */
	public static String fileName(Game game) {
		return GameFileNames.of(game, EXTENSION);
	}

	/**
	 * Writes the game's file. A one at a time folder has only its first enabled code on, as DeSmuME can't enforce it.
	 */
	public void write(Game game, OutputStream out) throws IOException {
		Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
		writer.write("; DeSmuME cheats file. VERSION 2.000" + NEWLINE);
		writer.write("Serial=" + serial(game.getSerial()) + NEWLINE);
		writer.write(NEWLINE + "; cheats list" + NEWLINE);

		for (Object entry : game.getTopLevelEntriesInOrder()) {
			if (entry instanceof Folder folder) {
				writeFolder(writer, folder);
			} else if (entry instanceof Code code && writable(code)) {
				writeCode(writer, code, code.isEnabled(), description(code));
			}
		}

		writer.write(NEWLINE);
		writer.flush();
	}

	/**
	 * A top level code's description: {@code <title> | <comment>}.
	 */
	public static String description(Code code) {
		return describe("", code);
	}

	/**
	 * A folder code's description as DeSmuME's {@code usrcheat.dat} import builds it:
	 * {@code <folder> [<folder note>]: <title> | <comment>}, leaving out empty parts, on one line and cut to fit.
	 */
	public static String description(Folder folder, Code code) {
		Objects.requireNonNull(folder, "folder");
		StringBuilder prefix = new StringBuilder();
		String folderName = Utf8Text.oneLine(folder.getTitle());
		String folderNote = Utf8Text.oneLine(folder.getComment());
		prefix.append(folderName);

		if (!folderNote.isEmpty()) {
			prefix.append(folderName.isEmpty() ? "" : " ").append('[').append(folderNote).append(']');
		}

		if (!folderName.isEmpty() || !folderNote.isEmpty()) {
			prefix.append(": ");
		}

		return describe(prefix.toString(), code);
	}

	/**
	 * The {@code Serial=} value, such as {@code NTR-AJLE-USA}: the serial and its region.
	 */
	public static String serial(String gameCode) {
		String code = gameCode == null ? "" : gameCode.strip();
		String region = code.isEmpty()
				? UNKNOWN_REGION
				: REGIONS.getOrDefault(code.charAt(code.length() - 1), UNKNOWN_REGION);
		return "NTR-%s-%s".formatted(code, region);
	}

	/**
	 * False for codes DeSmuME would skip or misread: no lines, placeholder lines, or over {@value #MAX_LINES} lines.
	 */
	public static boolean writable(Code code) {
		List<String> lines = code.getCodeLines();
		return !lines.isEmpty() && lines.size() <= MAX_LINES && UsercheatDatWriter.canWrite(lines);
	}

	/**
	 * A code's lines as DeSmuME writes them, each line's words joined and the lines comma separated.
	 */
	static String join(List<String> lines) {
		return String.join(",", lines).replace(" ", "").toUpperCase();
	}

	private static void writeFolder(Writer writer, Folder folder) throws IOException {
		boolean oneOn = false;

		for (Code code : folder.getCodes()) {
			if (writable(code)) {
				boolean enabled = code.isEnabled() && !(folder.isModifier() && oneOn);
				oneOn |= enabled;
				writeCode(writer, code, enabled, description(folder, code));
			}
		}
	}

	private static void writeCode(Writer writer, Code code, boolean enabled, String description) throws IOException {
		writer.write("AR %s %s ;%s%s".formatted(enabled ? "1" : "0", join(code.getCodeLines()), description, NEWLINE));
	}

	private static String describe(String prefix, Code code) {
		StringBuilder text = new StringBuilder(prefix);
		String title = Utf8Text.oneLine(code.getTitle());
		String comment = Utf8Text.oneLine(code.getComment());
		text.append(title);

		if (!comment.isEmpty()) {
			text.append(title.isEmpty() ? "" : " | ").append(comment);
		}

		return Utf8Text.cut(text.toString().strip(), MAX_DESCRIPTION_BYTES);
	}
}
