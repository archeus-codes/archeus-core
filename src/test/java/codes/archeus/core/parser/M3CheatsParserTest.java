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

import static codes.archeus.core.parser.M3CheatsWriterTest.code;
import static codes.archeus.core.parser.M3CheatsWriterTest.folder;
import static codes.archeus.core.parser.M3CheatsWriterTest.game;
import static codes.archeus.core.parser.M3CheatsWriterTest.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;
import codes.archeus.core.exception.UsercheatParserException;

class M3CheatsParserTest {
	private final M3CheatsParser parser = new M3CheatsParser();

	@Test
	void parse_writtenFile_readsBackEachGameFolderCodeAndNote() throws IOException {
		var game = game("C32P", "BE547C63", "Ace Attorney Investigations - Miles Edgeworth (Europe)");
		game.getCodes().add(code("Max Money", "By Datel", "02000008 0001869F"));

		var logic = folder("Logic Codes", "WARNING: Not every chapter.", 1);
		logic.getCodes().add(code("All Logic Clues", "(Press Select+Up)", "94000130 FFBB0000", "D2000000 00000000"));
		logic.getCodes().add(code("Datel", ""));
		game.getFolders().add(logic);

		var credits = game("AAAA", "00000000", "!Credits");
		credits.getCodes().add(code("Datel", ""));

		List<Game> read = parser.parse(new ByteArrayInputStream(write(credits, game))).games();

		assertThat(read).extracting(Game::getSerial, Game::getCrc32, Game::getTitle).containsExactly(
				Tuple.tuple("AAAA", "00000000", "!Credits"),
				Tuple.tuple("C32P", "BE547C63", "Ace Attorney Investigations - Miles Edgeworth (Europe)")
		);
		Game ace = read.get(1);
		assertThat(ace.getTopLevelEntriesInOrder())
				.extracting(entry -> entry instanceof Code code ? code.getTitle() : ((Folder) entry).getTitle())
				.containsExactly("Max Money", "Logic Codes");
		assertThat(ace.getCodes().getFirst().getComment()).isEqualTo("By Datel");
		Folder folder = ace.getFolders().getFirst();
		assertThat(folder.getComment()).isEqualTo("WARNING: Not every chapter.");
		assertThat(folder.getCodes()).extracting(Code::getTitle, Code::getComment, Code::getCodeLines).containsExactly(
				Tuple.tuple("All Logic Clues", "(Press Select+Up)", List.of("94000130 FFBB0000", "D2000000 00000000")),
				Tuple.tuple("Datel", "", List.of())
		);
	}

	@Test
	void parse_noteOfTheSeparatorsDashes_readsItAsANote() throws IOException {
		Game game = game("ABCD", "12345678", "Game");
		game.getCodes().add(code("Max Money", "", "0200000A 000000FF"));
		var items = folder("Items", "", 1);
		items.getCodes().add(code("Always Star", "----------", "02000004 00000001"));
		game.getFolders().add(items);

		Game read = parse(write(game)).getFirst();

		assertThat(read.getFolders().getFirst().getCodes().getFirst().getComment()).isEqualTo("---------- ");
	}

	@Test
	void parse_titlesAndNotesWithBrackets_readsThemSwapped() throws IOException {
		Game game = game("ABCD", "12345678", "Game");
		var items = folder("<Items>", "Hold <Select>", 1);
		items.getCodes().add(code("[L+R]All Cars", "Press <L+R>", "02000004 00000001"));
		game.getFolders().add(items);

		Folder read = parse(write(game)).getFirst().getFolders().getFirst();

		assertThat(read.getTitle()).isEqualTo("[Items]");
		assertThat(read.getComment()).isEqualTo("Hold [Select]");
		assertThat(read.getCodes())
				.extracting(Code::getTitle, Code::getComment)
				.containsExactly(Tuple.tuple("<L+R>All Cars", "Press [L+R]"));
	}

	@Test
	void parse_folderOfNoCodes_readsItEmpty() throws IOException {
		byte[] file = replaceText(
				write(withFolder()),
				"<Items:> = 1\r\n[Always Star]\r\n02000004 00000001\r\n\r\n",
				"<Items:> = 0\r\n"
		);

		Folder read = parse(file).getFirst().getFolders().getFirst();

		assertThat(read.getTitle()).isEqualTo("Items");
		assertThat(read.getCodes()).isEmpty();
	}

	@Test
	void parse_repeatedTextAcrossGames_sharesOneString() throws IOException {
		List<Game> read = parse(write(withItems("AAAA"), withItems("AAAB")));
		Folder first = read.getFirst().getFolders().getFirst();
		Folder second = read.getLast().getFolders().getFirst();
		Code firstCode = first.getCodes().getFirst();
		Code secondCode = second.getCodes().getFirst();

		assertThat(secondCode.getCodeLines().getFirst()).isEqualTo("0200000A 000000FF")
				.isSameAs(firstCode.getCodeLines().getFirst());
		assertThat(secondCode.getTitle()).isEqualTo("Max Money").isSameAs(firstCode.getTitle());
		assertThat(secondCode.getComment()).isEqualTo("By Datel").isSameAs(firstCode.getComment());
		assertThat(second.getTitle()).isEqualTo("Items").isSameAs(first.getTitle());
	}

	@Test
	void parse_twice_doesNotShareStringsBetweenParses() throws IOException {
		byte[] file = write(withItems("AAAA"));

		String first = parse(file).getFirst().getFolders().getFirst().getCodes().getFirst().getCodeLines().getFirst();
		String second = parse(file).getFirst().getFolders().getFirst().getCodes().getFirst().getCodeLines().getFirst();

		assertThat(second).isEqualTo(first).isNotSameAs(first);
	}

	@Test
	void parse_writtenFile_putsTheGamesInTheCheatFileToo() throws IOException {
		ParseResult result = parser.parse(new ByteArrayInputStream(write(oneCode("AAAA"))));

		assertThat(result.cheatFile().getGames()).isEqualTo(result.games()).hasSize(1);
	}

	@Test
	void parse_writtenFile_readsEveryGameAsOn() throws IOException {
		assertThat(parse(write(oneCode("AAAA"), oneCode("AAAB")))).allMatch(Game::isEnabled);
	}

	@Test
	void parse_folderCountTooLargeForAFolder_throws() throws IOException {
		byte[] file = replaceText(write(withFolder()), "<Items:> = 1", "<Items:> = 99999999999");

		assertThatThrownBy(() -> parse(file)).isInstanceOf(UsercheatParserException.class);
	}

	@Test
	void parse_gameThatIsntGbk_throws() throws IOException {
		byte[] file = write(oneCode("AAAA"));
		int text = file.length - 20;
		file[text] = (byte) 0x81;
		file[text + 1] = (byte) 0x7F;

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't GBK");
	}

	@Test
	void parse_indexEntryWithoutDht_throws() throws IOException {
		byte[] file = replace(write(oneCode("AAAA")), "AAAADHT", "AAAAXHT");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("index entry 0 is malformed");
	}

	@Test
	void parse_titleLineWithoutItsSemicolon_throws() throws IOException {
		byte[] file = replace(write(oneCode("AAAA")), ";Game AAAA", " Game AAAA");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't the game's title");
	}

	@Test
	void parse_folderAfterACodeWithoutTheSeparator_throws() throws IOException {
		Game game = oneCode("AAAA");
		var items = folder("Items", "", 1);
		items.getCodes().add(code("Always Star", "", "02000004 00000001"));
		game.getFolders().add(items);
		byte[] file = replaceText(write(game), "<----------:>\r\n<Items:>", "<Items:>");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("without the separator");
	}

	@Test
	void parse_separatorBeforeTheFirstEntry_throws() throws IOException {
		byte[] file = replaceText(write(withFolder()), "<Items:>", "<----------:>\r\n<Items:>");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("separates nothing");
	}

	@Test
	void parse_codeAfterAFolder_readsItAfterTheFolder() throws IOException {
		Game read = parse(write(withFolderThenCode())).getFirst();

		assertThat(read.getTopLevelEntriesInOrder())
				.extracting(entry -> entry instanceof Code code ? code.getTitle() : ((Folder) entry).getTitle())
				.containsExactly("Items", "Max Money");
	}

	@Test
	void parse_codeAfterAFolderWithoutTheSeparator_throws() throws IOException {
		byte[] file = replaceText(write(withFolderThenCode()), "<----------:>\r\n\r\n[Max Money]", "[Max Money]");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("without the separator");
	}

	@Test
	void parse_separatorBetweenTwoCodes_throws() throws IOException {
		Game game = oneCode("AAAA");
		Code alwaysStar = code("Always Star", "", "02000004 00000001");
		alwaysStar.setSortOrder(1);
		game.getCodes().add(alwaysStar);
		byte[] file = replaceText(write(game), "[Always Star]", "<----------:>\r\n[Always Star]");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't a folder");
	}

	@Test
	void parse_noGames_readsAnEmptyList() throws IOException {
		assertThat(parser.parse(new ByteArrayInputStream(write())).games()).isEmpty();
	}

	@Test
	void parse_gameThatDoesntFollowTheOneBeforeIt_throws() throws IOException {
		byte[] file = write(oneCode("AAAA"), oneCode("AAAB"));
		ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).putInt(2 + 23 + 15, 0);

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("AAAB");
	}

	@Test
	void parse_bytesAfterTheLastGame_throws() throws IOException {
		byte[] written = write(oneCode("AAAA"));
		byte[] file = Arrays.copyOf(written, written.length + 1);

		assertThatThrownBy(() -> parse(file)).isInstanceOf(UsercheatParserException.class);
	}

	@Test
	void parse_gameWithoutItsEnd_throws() throws IOException {
		byte[] file = write(oneCode("AAAA"));
		file[file.length - 1] = ' ';

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("\\0");
	}

	@Test
	void parse_folderHoldingFewerCodesThanItSays_throws() throws IOException {
		byte[] file = replace(write(withFolder()), "<Items:> = 1", "<Items:> = 2");

		assertThatThrownBy(() -> parse(file)).isInstanceOf(UsercheatParserException.class);
	}

	@Test
	void parse_lowercaseCodeLine_throws() throws IOException {
		byte[] file = replace(write(oneCode("AAAA")), "0200000A 000000FF", "0200000a 000000ff");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("isn't a code line");
	}

	@Test
	void parse_gameIdNamingAnotherGame_throws() throws IOException {
		byte[] file = replace(write(oneCode("AAAA")), ";Game ID: AAAA", ";Game ID: BBBB");

		assertThatThrownBy(() -> parse(file))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessageContaining("names a different game");
	}

	private List<Game> parse(byte[] file) throws IOException {
		return parser.parse(new ByteArrayInputStream(file)).games();
	}

	private static Game oneCode(String serial) {
		Game game = game(serial, "00000000", "Game " + serial);
		game.getCodes().add(code("Max Money", "", "0200000A 000000FF"));
		return game;
	}

	private static Game withFolder() {
		Game game = game("AAAA", "00000000", "Game");
		var items = folder("Items", "", 0);
		items.getCodes().add(code("Always Star", "", "02000004 00000001"));
		game.getFolders().add(items);
		return game;
	}

	private static Game withItems(String serial) {
		Game game = game(serial, "00000000", "Game " + serial);
		var items = folder("Items", "Hold A", 0);
		items.getCodes().add(code("Max Money", "By Datel", "0200000A 000000FF"));
		game.getFolders().add(items);
		return game;
	}

	private static Game withFolderThenCode() {
		Game game = withFolder();
		Code maxMoney = code("Max Money", "", "0200000A 000000FF");
		maxMoney.setSortOrder(1);
		game.getCodes().add(maxMoney);
		return game;
	}

	/**
	 * The file with one ASCII text in its only game swapped for another of any length, its index fixed to match.
	 */
	private static byte[] replaceText(byte[] file, String from, String to) {
		String text = new String(file, StandardCharsets.ISO_8859_1);
		assertThat(text).contains(from);
		byte[] replaced = text.replace(from, to).getBytes(StandardCharsets.ISO_8859_1);
		ByteBuffer.wrap(replaced).order(ByteOrder.LITTLE_ENDIAN).putInt(2 + 19, replaced.length - (2 + 23 + 2));
		return replaced;
	}

	/**
	 * The file with one ASCII text swapped for another of the same length, so the index still fits.
	 */
	private static byte[] replace(byte[] file, String from, String to) {
		String text = new String(file, StandardCharsets.ISO_8859_1);
		assertThat(text).contains(from);
		assertThat(to).hasSameSizeAs(from);
		return text.replace(from, to).getBytes(StandardCharsets.ISO_8859_1);
	}
}
