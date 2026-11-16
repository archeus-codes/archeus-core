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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

/**
 * The emulator formats with one file per game. Each writes only the codes it can hold, and names the file as
 * {@link GameFileNames#of} does.
 */
public enum GameFileFormat {
	MELONDS(MelonDsWriter.EXTENSION) {
		/**
		 * melonDS keeps a code with no lines as a name, but fails the whole file on placeholder lines.
		 */
		@Override
		public boolean holds(Code code) {
			return UsercheatDatWriter.canWrite(code.getCodeLines());
		}

		@Override
		void writeFile(Game game, OutputStream out) throws IOException {
			new MelonDsWriter().write(game, out);
		}
	},
	RETROARCH(RetroArchWriter.EXTENSION) {
		@Override
		public boolean holds(Code code) {
			return RetroArchWriter.writable(code);
		}

		@Override
		void writeFile(Game game, OutputStream out) throws IOException {
			new RetroArchWriter().write(game, out);
		}
	},
	DESMUME(DesmumeWriter.EXTENSION) {
		@Override
		public boolean holds(Code code) {
			return DesmumeWriter.writable(code);
		}

		@Override
		void writeFile(Game game, OutputStream out) throws IOException {
			new DesmumeWriter().write(game, out);
		}
	};

	private final String key;
	private final String extension;

	GameFileFormat(String extension) {
		this.key = extension.substring(1);
		this.extension = extension;
	}

	/**
	 * The format with the key, such as {@code mch}, or empty for none.
	 */
	public static Optional<GameFileFormat> of(String key) {
		return Arrays.stream(values()).filter(format -> format.key.equals(key)).findFirst();
	}

	/**
	 * The format's short name: its extension without the dot, such as {@code mch}.
	 */
	public String key() {
		return key;
	}

	/**
	 * The file extension, with its dot.
	 */
	public String extension() {
		return extension;
	}

	/**
	 * Whether the format can hold the code. A code it can't, such as a template with placeholders, is left out.
	 */
	public abstract boolean holds(Code code);

	abstract void writeFile(Game game, OutputStream out) throws IOException;

	/**
	 * The game's file name, such as {@code Animal Crossing - Wild World (USA) [ADME 8F7851CB].mch}.
	 */
	public String fileName(Game game) {
		return GameFileNames.of(game, extension);
	}

	/**
	 * Whether the game's file would hold any code.
	 */
	public boolean holdsAny(Game game) {
		return game.getCodes().stream().anyMatch(this::holds)
				|| game.getFolders().stream().flatMap(folder -> folder.getCodes().stream()).anyMatch(this::holds);
	}

	/**
	 * Writes the game's file: only the codes the format holds ({@link #held}).
	 */
	public void write(Game game, OutputStream out) throws IOException {
		writeFile(held(game), out);
	}

	/**
	 * A copy of the game with only the codes the format holds, and without the folders left empty.
	 */
	public Game held(Game game) {
		List<Folder> folders = new ArrayList<>();

		for (Folder folder : game.getFolders()) {
			List<Code> codes = heldCodes(folder.getCodes());

			if (!codes.isEmpty()) {
				folders.add(folder.withCodes(codes));
			}
		}

		return game.withEntries(heldCodes(game.getCodes()), folders);
	}

	private List<Code> heldCodes(List<Code> codes) {
		return new ArrayList<>(codes.stream().filter(this::holds).toList());
	}
}
