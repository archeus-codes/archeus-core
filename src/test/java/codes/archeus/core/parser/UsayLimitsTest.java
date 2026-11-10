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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

class UsayLimitsTest {
	@Test
	void apply_folderOverTheLimit_splitsItIntoNumberedParts() {
		Folder items = folder("Items", 600, true);
		Game game = game(items);

		UsayLimits.Result result = UsayLimits.apply(List.of(game));
		Game output = result.games().getFirst();

		assertThat(result.split()).containsExactly("Game: Items (3 parts)");
		assertThat(result.changed()).isTrue();
		assertThat(output.getFolders()).extracting(Folder::getTitle)
				.containsExactly("Items (1/3)", "Items (2/3)", "Items (3/3)");
		assertThat(output.getFolders()).extracting(folder -> folder.getCodes().size()).containsExactly(255, 255, 90);
		assertThat(output.getFolders()).allSatisfy(part -> {
			assertThat(part.isModifier()).isTrue();
			assertThat(part.getComment()).isEqualTo("Pick one");
			assertThat(part.getSortOrder()).isEqualTo(items.getSortOrder());
		});
		assertThat(output.getFolders().get(1).getCodes().getFirst()).isSameAs(items.getCodes().get(255));
	}

	@Test
	void apply_partsOfAFolder_keepTheirPlaceAmongTheGamesEntries() {
		Code before = code("Before", 1, 0);
		Folder items = folder("Items", 300, false);
		items.setSortOrder(1);
		Code after = code("After", 1, 2);
		Game game = game(items);
		game.setCodes(new ArrayList<>(List.of(before, after)));

		Game output = UsayLimits.apply(List.of(game)).games().getFirst();

		assertThat(output.getTopLevelEntriesInOrder())
				.extracting(entry -> entry instanceof Code code ? code.getTitle() : ((Folder) entry).getTitle())
				.containsExactly("Before", "Items (1/2)", "Items (2/2)", "After");
	}

	@Test
	void apply_folderWithinTheLimit_keepsTheSameFolder() {
		Folder small = folder("Small", 255, false);

		UsayLimits.Result result = UsayLimits.apply(List.of(game(small)));

		assertThat(result.games().getFirst().getFolders()).containsExactly(small);
		assertThat(result.changed()).isFalse();
	}

	@Test
	void apply_codeOverTheLineLimit_leavesItOut() {
		Code tooLong = code("Too long", 256, 0);
		Code fine = code("Fine", 255, 1);
		Game game = game();
		game.setCodes(new ArrayList<>(List.of(tooLong, fine)));

		UsayLimits.Result result = UsayLimits.apply(List.of(game));

		assertThat(result.games().getFirst().getCodes()).containsExactly(fine);
		assertThat(result.leftOut()).containsExactly("Game: Too long");
	}

	@Test
	void apply_folderLeftEmptyByLongCodes_isLeftOut() {
		Folder onlyLong = new Folder();
		onlyLong.setTitle("Only long");
		onlyLong.setCodes(new ArrayList<>(List.of(code("Too long", 300, 0))));
		Folder empty = new Folder();
		empty.setTitle("Empty from the start");

		Game output = UsayLimits.apply(List.of(game(onlyLong, empty))).games().getFirst();

		assertThat(output.getFolders()).extracting(Folder::getTitle).containsExactly("Empty from the start");
	}

	@Test
	void apply_games_leavesThemUnchanged() {
		Folder items = folder("Items", 600, true);
		Game game = game(items);

		UsayLimits.apply(List.of(game));

		assertThat(game.getFolders()).containsExactly(items);
		assertThat(items.getTitle()).isEqualTo("Items");
		assertThat(items.getCodes()).hasSize(600);
	}

	private static Game game(Folder... folders) {
		var game = new Game();
		game.setTitle("Game");
		game.setFolders(new ArrayList<>(List.of(folders)));
		return game;
	}

	private static Folder folder(String title, int codes, boolean modifier) {
		var folder = new Folder();
		folder.setTitle(title);
		folder.setComment("Pick one");
		folder.setModifier(modifier);
		List<Code> list = new ArrayList<>();

		for (int i = 0; i < codes; ++i) {
			list.add(code(title + " " + i, 1, i));
		}

		folder.setCodes(list);
		return folder;
	}

	private static Code code(String title, int lines, int sortOrder) {
		var code = new Code();
		code.setTitle(title);
		code.setSortOrder(sortOrder);
		code.setCodeLines(new ArrayList<>(Collections.nCopies(lines, "12345678 00000001")));
		return code;
	}
}
