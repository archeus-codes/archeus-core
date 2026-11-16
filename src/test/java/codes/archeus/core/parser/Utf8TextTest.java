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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import codes.archeus.core.exception.UsercheatParserException;

class Utf8TextTest {
	/** 10 bytes in UTF-8 (a is 1, é 2, € 3 and 😀 4) but 5 chars, since 😀 takes two. */
	private static final String MIXED = "aé€😀";

	@Test
	void prefixWithin_oneToFourByteCharacters_keepsEachCharacterWhole() {
		assertThat(Utf8Text.bytes(MIXED)).isEqualTo(10);
		assertThat(Utf8Text.prefixWithin(MIXED, 10)).isEqualTo(5);
		assertThat(Utf8Text.prefixWithin(MIXED, 6)).isEqualTo(3);
		assertThat(Utf8Text.prefixWithin(MIXED, 1)).isOne();
		assertThat(Utf8Text.prefixWithin(MIXED, 0)).isZero();
	}

	@Test
	void prefixWithin_limitInsideAMultibyteCharacter_stopsBeforeIt() {
		assertThat(Utf8Text.prefixWithin(MIXED, 2)).isOne();
		assertThat(Utf8Text.prefixWithin(MIXED, 5)).isEqualTo(2);
		assertThat(Utf8Text.prefixWithin(MIXED, 9)).isEqualTo(3);
	}

	@Test
	void prefixWithin_loneSurrogate_countsOneByteAsGetBytesDoes() {
		String text = "\uD800x";

		assertThat(Utf8Text.bytes(text)).isEqualTo(2);
		assertThat(Utf8Text.prefixWithin(text, 1)).isOne();
		assertThat(Utf8Text.prefixWithin(text, 2)).isEqualTo(2);
	}

	@Test
	void decode_utf8_returnsTheText() {
		assertThat(Utf8Text.decode(MIXED.getBytes(StandardCharsets.UTF_8), "Test file")).isEqualTo(MIXED);
	}

	@Test
	void decode_bytesThatArentUtf8_throwsWithTheirLine() {
		byte[] bytes = "ok\nCafé\n".getBytes(StandardCharsets.ISO_8859_1);

		assertThatThrownBy(() -> Utf8Text.decode(bytes, "Test file"))
				.isInstanceOf(UsercheatParserException.class)
				.hasMessage("Test file line 2 isn't UTF-8");
	}

	@Test
	void cut_textOverTheLimit_endsAtAWordWithTheCutMark() {
		String cut = Utf8Text.cut("one two three four", 12);

		assertThat(cut).isEqualTo("one two" + Utf8Text.CUT_MARK);
		assertThat(Utf8Text.bytes(cut)).isLessThanOrEqualTo(12);
	}

	@Test
	void oneLine_lineBreaks_joinsThemWithOneSpace() {
		assertThat(Utf8Text.oneLine("  Press A\r\n  then B  ")).isEqualTo("Press A then B");
		assertThat(Utf8Text.oneLine(null)).isEmpty();
	}
}
