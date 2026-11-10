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

import java.util.ArrayList;
import java.util.List;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

/**
 * Applies the limits of a USAY record (EDGE and CycloDS Evolution) to games: one byte counts a folder's codes and one
 * byte counts a code's lines. A folder with more codes is split into parts, such as {@code Items (1/8)}, each keeping
 * the folder's comment, its one at a time setting and its place among the game's entries. A code with more lines
 * can't be split, so it's left out, and so is a folder that leaves empty. The games passed in aren't changed.
 */
public final class UsayLimits {
	/** The most codes a USAY folder can hold. */
	public static final int MAX_FOLDER_CODES = 255;

	/** The most lines a USAY code can hold. */
	public static final int MAX_CODE_LINES = 255;

	private UsayLimits() {
	}

	/**
	 * The games as a USAY file can hold them.
	 *
	 * @param games   the games as the file holds them
	 * @param split   the folders split into parts, as {@code game title: folder title (n parts)}
	 * @param leftOut the codes left out, as {@code game title: code title}
	 */
	public record Result(List<Game> games, List<String> split, List<String> leftOut) {
		/**
		 * Whether applying the limits changed anything.
		 */
		public boolean changed() {
			return !split.isEmpty() || !leftOut.isEmpty();
		}
	}

	public static Result apply(List<Game> games) {
		List<String> split = new ArrayList<>();
		List<String> leftOut = new ArrayList<>();
		List<Game> applied = games.stream().map(game -> apply(game, split, leftOut)).toList();
		return new Result(applied, split, leftOut);
	}

	private static Game apply(Game game, List<String> split, List<String> leftOut) {
		var copy = new Game();
		copy.setTitle(game.getTitle());
		copy.setSerial(game.getSerial());
		copy.setCrc32(game.getCrc32());
		copy.setFileOffset(game.getFileOffset());
		copy.setEnabled(game.isEnabled());
		copy.setMasterCode(game.getMasterCode());
		copy.setCodes(withinLineLimit(game, game.getCodes(), leftOut));

		List<Folder> folders = new ArrayList<>();

		for (Folder folder : game.getFolders()) {
			List<Code> codes = withinLineLimit(game, folder.getCodes(), leftOut);

			if (codes.isEmpty() && !folder.getCodes().isEmpty()) {
				continue;
			}

			if (codes.size() <= MAX_FOLDER_CODES) {
				folders.add(codes.size() == folder.getCodes().size() ? folder : part(folder, folder.getTitle(), codes));
				continue;
			}

			int parts = (codes.size() + MAX_FOLDER_CODES - 1) / MAX_FOLDER_CODES;
			split.add(game.getTitle() + ": " + folder.getTitle() + " (" + parts + " parts)");

			for (int i = 0; i < parts; ++i) {
				int from = i * MAX_FOLDER_CODES;
				List<Code> partCodes = codes.subList(from, Math.min(codes.size(), from + MAX_FOLDER_CODES));
				folders.add(part(folder, folder.getTitle() + " (" + (i + 1) + "/" + parts + ")", partCodes));
			}
		}

		copy.setFolders(folders);

		return copy;
	}

	/**
	 * The codes within the line limit, noting the ones left out.
	 */
	private static List<Code> withinLineLimit(Game game, List<Code> codes, List<String> leftOut) {
		List<Code> within = new ArrayList<>(codes.size());

		for (Code code : codes) {
			if (code.getCodeLines().size() > MAX_CODE_LINES) {
				leftOut.add(game.getTitle() + ": " + code.getTitle());
			} else {
				within.add(code);
			}
		}

		return within;
	}

	/**
	 * A folder like the original, with these codes. It takes the original's sort order, so it keeps its place; parts
	 * of one folder share that order, and the stable sort in {@link Game#getTopLevelEntriesInOrder()} keeps them in
	 * sequence.
	 */
	private static Folder part(Folder original, String title, List<Code> codes) {
		var part = new Folder();
		part.setTitle(title);
		part.setComment(original.getComment());
		part.setModifier(original.isModifier());
		part.setSortOrder(original.getSortOrder());
		part.setCodes(new ArrayList<>(codes));
		return part;
	}
}
