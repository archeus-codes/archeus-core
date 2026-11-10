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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeCheatsWriterTest {

	@Test
	void writeEdge_roundTrip_preservesGameCount() throws IOException {
		List<Game> games = parseEdge();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(null, games, out);

		ParseResult result = new EdgeCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		assertThat(result.games()).hasSize(4);
	}

	@Test
	void writeEdge_roundTrip_preservesFirstGameMetadataAndCodes() throws IOException {
		List<Game> games = parseEdge();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(null, games, out);

		ParseResult result = new EdgeCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		Game jeopardy = result.games().getFirst();
		assertThat(jeopardy.getSerial()).isEqualTo("BJDE");
		assertThat(jeopardy.getCrc32()).isEqualTo("D4EC931D");
		assertThat(jeopardy.getTitle()).isEqualTo("Jeopardy! (USA)");
		assertThat(jeopardy.getCodes()).hasSize(5);

		var alwaysCorrect = jeopardy.getCodes().getFirst();
		assertThat(alwaysCorrect.getTitle()).isEqualTo("Always Correct Answer");
		assertThat(alwaysCorrect.getComment()).isEqualTo("By Demonic722");
		assertThat(alwaysCorrect.getCodeLines()).hasSize(4);
	}

	@Test
	void writeCevo_roundTrip_preservesGameCount() throws IOException {
		List<Game> games = parseCevo();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EvoCheatsWriter().write(null, games, out);

		ParseResult result = new EvoCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		assertThat(result.games()).hasSize(4);
	}

	@Test
	void writeCevo_roundTrip_preservesFirstGameMetadataAndCodes() throws IOException {
		List<Game> games = parseCevo();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EvoCheatsWriter().write(null, games, out);

		ParseResult result = new EvoCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		Game jeopardy = result.games().getFirst();
		assertThat(jeopardy.getSerial()).isEqualTo("BJDE");
		assertThat(jeopardy.getCrc32()).isEqualTo("D4EC931D");
		assertThat(jeopardy.getTitle()).isEqualTo("Jeopardy! (USA)");
		assertThat(jeopardy.getCodes()).hasSize(5);
	}

	@Test
	void writeEdge_roundTrip_preservesModifierFolders() throws IOException {
		List<Game> games = parseEdge();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(null, games, out);

		ParseResult result = new EdgeCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		Game pokemon = result.games().stream()
				.filter(g -> "APHE".equals(g.getSerial()))
				.findFirst()
				.orElseThrow();

		assertThat(pokemon.getFolders()).extracting(Folder::isModifier).containsExactly(false, false, false, true);
	}

	@Test
	void writeEdge_roundTrip_preservesPokemonFolderCodeCounts() throws IOException {
		List<Game> games = parseEdge();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(null, games, out);

		ParseResult result = new EdgeCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		Game pokemon = result.games().stream()
				.filter(g -> "APHE".equals(g.getSerial()))
				.findFirst()
				.orElseThrow();

		assertThat(pokemon.getFolders()).hasSize(4);
		assertThat(folderByTitle(pokemon, "Miscellaneous Codes").getCodes()).hasSize(5);
		assertThat(folderByTitle(pokemon, "Dungeon Codes").getCodes()).hasSize(3);
		assertThat(folderByTitle(pokemon, "Dungeon Party Codes").getCodes()).hasSize(2);
		assertThat(folderByTitle(pokemon, "Experience Multiplier Codes").getCodes()).hasSize(8);
	}

	@Test
	void writeEdge_roundTrip_preservesInterleavedOrderOfTopLevelCodesAndFolders() throws IOException {
		List<Game> games = parseEdge();
		Game jeopardy = games.stream()
				.filter(g -> "BJDE".equals(g.getSerial()))
				.findFirst()
				.orElseThrow();

		List<String> originalOrder = mergedTitlesInOrder(jeopardy);
		assertThat(originalOrder).containsExactly(
				"Always Correct Answer",
				"Miscellaneous Codes",
				"Get Money From Correct And Incorrect Answers",
				"Player 1 Max Money",
				"Player 2 No Money",
				"Player 3 No Money"
		);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(null, games, out);

		ParseResult result = new EdgeCheatsParser().parse(new ByteArrayInputStream(out.toByteArray()));
		Game roundTripped = result.games().stream()
				.filter(g -> "BJDE".equals(g.getSerial()))
				.findFirst()
				.orElseThrow();

		assertThat(mergedTitlesInOrder(roundTripped)).containsExactlyElementsOf(originalOrder);
	}

	@Test
	void writeEdge_folderOver255Codes_roundTripsAsPartsWithEveryCodeAndItsEnabledFlag() throws IOException {
		var game = new Game();
		game.setTitle("Big Folder Game");
		game.setSerial("BIGF");
		game.setCrc32("0BADC0DE");

		var folder = new Folder();
		folder.setTitle("Items");
		folder.setModifier(true);

		List<Code> codes = new ArrayList<>();

		for (int i = 0; i < 600; ++i) {
			var code = new Code();
			code.setTitle("Item " + i);
			code.setEnabled(i == 300);
			code.setCodeLines(new ArrayList<>(List.of("12345678 %08X".formatted(i))));
			codes.add(code);
		}

		folder.setCodes(codes);
		game.setFolders(new ArrayList<>(List.of(folder)));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new EdgeCheatsWriter().write(null, List.of(game), out);
		Game read = new EdgeCheatsParser().parse(new ByteArrayInputStream(out.toByteArray())).games().getFirst();

		assertThat(read.getFolders()).extracting(Folder::getTitle)
				.containsExactly("Items (1/3)", "Items (2/3)", "Items (3/3)");
		List<Code> readCodes = read.getFolders().stream().flatMap(part -> part.getCodes().stream()).toList();
		assertThat(readCodes).extracting(Code::getTitle).containsExactlyElementsOf(codes.stream().map(Code::getTitle).toList());
		assertThat(readCodes).filteredOn(Code::isEnabled).extracting(Code::getTitle).containsExactly("Item 300");
	}

	private List<String> mergedTitlesInOrder(Game game) {
		List<Object> merged = new ArrayList<>();
		merged.addAll(game.getCodes());
		merged.addAll(game.getFolders());
		merged.sort(Comparator.comparingInt(
				entry -> entry instanceof Code code ? code.getSortOrder() : ((Folder) entry).getSortOrder()
		));

		return merged.stream()
				.map(entry -> entry instanceof Code code ? code.getTitle() : ((Folder) entry).getTitle())
				.toList();
	}

	private Folder folderByTitle(Game game, String title) {
		return game.getFolders().stream()
				.filter(f -> title.equals(f.getTitle()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Folder not found: " + title));
	}

	private List<Game> parseEdge() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/cheats.dat")) {
			assertThat(in).isNotNull();
			return new EdgeCheatsParser().parse(in).games();
		}
	}

	private List<Game> parseCevo() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/user.evoCheats")) {
			assertThat(in).isNotNull();
			return new EvoCheatsParser().parse(in).games();
		}
	}
}
