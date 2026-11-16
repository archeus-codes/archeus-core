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
import java.util.stream.Stream;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

/**
 * Applies the limits of an M3 cheat database ({@code cheat_EN.db}) to games. A code with placeholder lines is left out,
 * and so is a folder left with no codes, whether its codes were left out or it had none. The file has no master
 * codes, one at a time folders or codes switched on, and characters GBK lacks become {@code ?}, so those are listed
 * too. The games passed in aren't changed.
 */
public final class M3Limits {
	private M3Limits() {
	}

	/**
	 * The games as an M3 file holds them, and what it changes, each as {@code game title: entry title} or the game's
	 * title.
	 *
	 * @param leftOut          the codes with placeholder lines
	 * @param emptiedByLeftOut the folders those codes emptied
	 * @param emptyToBeginWith the folders with no codes to begin with
	 * @param masterCodes      the games with a master code
	 * @param oneAtATime       the one at a time folders, saved as ordinary folders
	 * @param switchedOn       the codes switched on, saved switched off
	 * @param lostText         the games with text GBK lacks
	 */
	public record Result(
			List<Game> games,
			List<String> leftOut,
			List<String> emptiedByLeftOut,
			List<String> emptyToBeginWith,
			List<String> masterCodes,
			List<String> oneAtATime,
			List<String> switchedOn,
			List<String> lostText
	) {
		/**
		 * Whether the file differs from the games in any way.
		 */
		public boolean changed() {
			return Stream.of(leftOut, emptiedByLeftOut, emptyToBeginWith, masterCodes, oneAtATime, switchedOn, lostText)
					.anyMatch(list -> !list.isEmpty());
		}
	}

	/**
	 * The games as an M3 file holds them, with what it changes ({@link Result}).
	 */
	public static Result apply(List<Game> games) {
		return apply(games, true);
	}

	/**
	 * The games as an M3 file holds them, for the writer. It skips checking their text against GBK, which only the
	 * list of changes needs.
	 */
	static List<Game> held(List<Game> games) {
		return apply(games, false).games();
	}

	/**
	 * False for a code with placeholder lines, such as {@code 0200???? 00000063}. A code with no lines is kept.
	 */
	public static boolean writable(Code code) {
		return UsercheatDatWriter.canWrite(code.getCodeLines());
	}

	private static Result apply(List<Game> games, boolean checkText) {
		var result = new Result(
				new ArrayList<>(games.size()),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>(),
				new ArrayList<>()
		);

		for (Game game : games) {
			result.games().add(apply(game, result, checkText));
		}

		return result;
	}

	private static Game apply(Game game, Result result, boolean checkText) {
		List<Code> topLevel = heldCodes(game, game.getCodes(), result);

		if (MasterCode.isCustom(game.getMasterCode())) {
			result.masterCodes().add(game.getTitle());
		}

		List<Folder> folders = new ArrayList<>();

		for (Folder folder : game.getFolders()) {
			List<Code> codes = heldCodes(game, folder.getCodes(), result);

			if (codes.isEmpty()) {
				List<String> list = folder.getCodes().isEmpty() ? result.emptyToBeginWith() : result.emptiedByLeftOut();
				list.add(game.getTitle() + ": " + folder.getTitle());
				continue;
			}

			if (folder.isModifier()) {
				result.oneAtATime().add(game.getTitle() + ": " + folder.getTitle());
			}

			folders.add(codes.size() == folder.getCodes().size() ? folder : folder.withCodes(codes));
		}

		Game copy = game.withEntries(topLevel, folders);

		if (checkText && !holdsAllText(copy)) {
			result.lostText().add(game.getTitle());
		}

		return copy;
	}

	/**
	 * The codes the file holds. Codes left out are listed in {@link Result#leftOut()}, and codes switched on in
	 * {@link Result#switchedOn()}.
	 */
	private static List<Code> heldCodes(Game game, List<Code> codes, Result result) {
		List<Code> held = new ArrayList<>(codes.size());

		for (Code code : codes) {
			if (!writable(code)) {
				result.leftOut().add(game.getTitle() + ": " + code.getTitle());
				continue;
			}

			if (code.isEnabled()) {
				result.switchedOn().add(game.getTitle() + ": " + code.getTitle());
			}

			held.add(code);
		}

		return held;
	}

	/**
	 * Whether the file holds the game's title and every folder's and code's title and note as they read.
	 */
	private static boolean holdsAllText(Game game) {
		Stream<String> folderText = game.getFolders().stream()
				.flatMap(folder -> Stream.of(folder.getTitle(), folder.getComment()));
		Stream<String> codeText = Stream.concat(
				game.getCodes().stream(),
				game.getFolders().stream().flatMap(folder -> folder.getCodes().stream())
		).flatMap(code -> Stream.of(code.getTitle(), code.getComment()));
		return Stream.concat(Stream.of(game.getTitle()), Stream.concat(folderText, codeText))
				.allMatch(M3CheatsWriter::holdsWhole);
	}
}
