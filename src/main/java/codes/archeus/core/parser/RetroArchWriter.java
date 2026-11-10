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
 * Writes one game's codes as a RetroArch cheat file ({@code .cht}), laid out like libretro's own DS cheat files:
 * <pre>
 * cheats = 3
 *
 * cheat0_desc = "Infinite Health"
 * cheat0_code = "52007DEC+AA000018"
 * cheat0_enable = false
 *
 * cheat1_desc = "Weather Mod Codes"
 *
 * cheat2_desc = "Sunny"
 * cheat2_code = "12345678+00000001"
 * cheat2_enable = false
 * </pre>
 * An entry with only a description is a folder's heading, so top level codes come first. Descriptions are titles only,
 * since the format has no comments. Codes that can't run are left out ({@link #writable}).
 */
public class RetroArchWriter {
	public static final String EXTENSION = ".cht";
	public static final String UNTITLED = "(untitled)";

	private static final Pattern LINE_BREAKS = Pattern.compile("\\s*[\\r\\n]+\\s*");

	/**
	 * The file name for a game ({@link GameFileNames#of}), ending in {@code .cht}.
	 */
	public static String fileName(Game game) {
		return GameFileNames.of(game, EXTENSION);
	}

	public void write(Game game, OutputStream out) throws IOException {
		List<Entry> entries = new ArrayList<>();

		for (Code code : game.getCodes()) {
			if (writable(code)) {
				entries.add(Entry.of(code));
			}
		}

		for (Folder folder : game.getFolders()) {
			entries.add(new Entry(folder.getTitle(), null, false));

			for (Code code : folder.getCodes()) {
				if (writable(code)) {
					entries.add(Entry.of(code));
				}
			}
		}

		Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
		writer.write("cheats = " + entries.size() + "\n");

		for (int i = 0; i < entries.size(); ++i) {
			Entry entry = entries.get(i);
			writer.write("\ncheat" + i + "_desc = \"" + description(entry.title()) + "\"\n");

			if (entry.code() != null) {
				writer.write("cheat" + i + "_code = \"" + entry.code() + "\"\n");
				writer.write("cheat" + i + "_enable = " + entry.enabled() + "\n");
			}
		}

		writer.flush();
	}

	/**
	 * A title RetroArch reads back whole, since it ends a value at the next quote: on one line, with {@code "} made
	 * {@code '} and no trailing backslash. A blank title becomes {@value #UNTITLED}.
	 */
	public static String description(String title) {
		String text = title == null ? "" : LINE_BREAKS.matcher(title.strip()).replaceAll(" ");
		text = text.replace('"', '\'').replaceAll("\\\\+$", "").strip();
		return text.isEmpty() ? UNTITLED : text;
	}

	/**
	 * A code's lines as RetroArch's cores read them: every 8-digit word, joined by {@code +}.
	 */
	static String join(List<String> lines) {
		return String.join("+", lines).replace(' ', '+').toUpperCase();
	}

	/**
	 * False for codes with no lines (the "!Credits" names) or with placeholder lines.
	 */
	public static boolean writable(Code code) {
		return !code.getCodeLines().isEmpty() && UsercheatDatWriter.canWrite(code.getCodeLines());
	}

	/**
	 * @param code the joined code, or null for a folder's heading
	 */
	private record Entry(String title, String code, boolean enabled) {
		static Entry of(Code code) {
			return new Entry(code.getTitle(), join(code.getCodeLines()), code.isEnabled());
		}
	}
}
